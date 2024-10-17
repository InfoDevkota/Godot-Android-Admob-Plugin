package shinnil.godot.plugin.android.godotadmob;

import static com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotLib;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("unused")
public class GodotAdMob extends GodotPlugin {
    private final Activity activity; // The main activity of the game

    private boolean isReal = false; // Store if is real or not
    private boolean isForChildDirectedTreatment = false; // Store if is children directed treatment desired
    private boolean isPersonalized = true; // ads are personalized by default, GDPR compliance within the European Economic Area may require you to disable personalization.
    private String maxAdContentRating = ""; // Store maxAdContentRating ("G", "PG", "T" or "MA")
    private Bundle extras = null;

    private FrameLayout layout = null; // Store the layout

    private RewardedVideo rewardedVideo = null; // Rewarded Video object
    private RewardedInterstitial rewardedInterstitial = null; // Rewarded Interstitial object
    private Interstitial interstitial = null; // Interstitial object
    private Banner banner = null; // Banner object
    private CMP cmp; // Google Consent Management Platform (CMP)


    public GodotAdMob(Godot godot) {
        super(godot);
        this.activity = getActivity();
    }

    // create and add a new layout to Godot
    @Override
    public View onMainCreate(Activity activity) {
        layout = new FrameLayout(activity);
        return layout;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotAdMob";
    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("on_admob_initialized"));

        signals.add(new SignalInfo("on_admob_ad_loaded"));
        signals.add(new SignalInfo("on_admob_banner_failed_to_load", Integer.class));

        signals.add(new SignalInfo("on_interstitial_loaded"));
        signals.add(new SignalInfo("on_interstitial_failed_to_load", Integer.class));
        signals.add(new SignalInfo("on_interstitial_close"));
        signals.add(new SignalInfo("on_interstitial_opened"));
        signals.add(new SignalInfo("on_interstitial_clicked"));
        signals.add(new SignalInfo("on_interstitial_impression"));

        signals.add(new SignalInfo("on_rewarded_video_ad_closed"));
        signals.add(new SignalInfo("on_rewarded_video_ad_failed_to_load", Integer.class));
        signals.add(new SignalInfo("on_rewarded_video_ad_loaded"));
        signals.add(new SignalInfo("on_rewarded_video_ad_opened"));

        signals.add(new SignalInfo("on_rewarded_interstitial_ad_loaded"));
        signals.add(new SignalInfo("on_rewarded_interstitial_ad_opened"));
        signals.add(new SignalInfo("on_rewarded_interstitial_ad_closed"));
        signals.add(new SignalInfo("on_rewarded_interstitial_ad_failed_to_load", Integer.class));
        signals.add(new SignalInfo("on_rewarded_interstitial_ad_failed_to_show", Integer.class));

        signals.add(new SignalInfo("on_rewarded", String.class, Integer.class));
        signals.add(new SignalInfo("on_rewarded_clicked"));
        signals.add(new SignalInfo("on_rewarded_impression"));

        signals.add(new SignalInfo("on_consent_info_update_success"));
        signals.add(
                new SignalInfo("on_consent_info_update_failure",
                        Integer.class, String.class));
        signals.add(new SignalInfo("on_app_can_request_ads", Integer.class));

        return signals;
    }

    /* Init
     * ********************************************************************** */

    /**
     * Prepare for work with AdMob
     *
     * @param isReal     Tell if the environment is for real or test
     */
    @UsedByGodot
    public void init(boolean isReal) {
        this.initWithContentRating(isReal, false, true, "");
    }

    /**
     * Init with content rating additional options
     *
     * @param isReal                      Tell if the environment is for real or test
     * @param isForChildDirectedTreatment Target audience is children.
     * @param isPersonalized              If ads should be personalized or not.
     *                                    GDPR compliance within the European Economic Area requires that you
     *                                    disable ad personalization if the user does not wish to opt into
     *                                    ad personalization.
     * @param maxAdContentRating          must be "G", "PG", "T" or "MA"
     */
    @UsedByGodot
    public void initWithContentRating(
            boolean isReal,
            boolean isForChildDirectedTreatment,
            boolean isPersonalized,
            String maxAdContentRating) {

        this.isReal = isReal;
        this.isForChildDirectedTreatment = isForChildDirectedTreatment;
        this.isPersonalized = isPersonalized;
        this.maxAdContentRating = maxAdContentRating;

        this.setRequestConfigurations();

        if (!isPersonalized) {
            // https://developers.google.com/admob/android/eu-consent#forward_consent_to_the_google_mobile_ads_sdk
            if (extras == null) {
                extras = new Bundle();
            }
            extras.putString("npa", "1");
        }

        Log.d("godot", "AdMob: init with content rating options");
    }


    private void setRequestConfigurations() {
        if (!this.isReal) {
            List<String> testDeviceIds = Arrays.asList(AdRequest.DEVICE_ID_EMULATOR, getAdMobDeviceId());
            RequestConfiguration requestConfiguration = MobileAds.getRequestConfiguration()
                    .toBuilder()
                    .setTestDeviceIds(testDeviceIds)
                    .build();
            MobileAds.setRequestConfiguration(requestConfiguration);
        }

        if (this.isForChildDirectedTreatment) {
            RequestConfiguration requestConfiguration = MobileAds.getRequestConfiguration()
                    .toBuilder()
                    .setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
                    .build();
            MobileAds.setRequestConfiguration(requestConfiguration);
        }

        // StringEquality false positive
        //noinspection StringEquality
        if (this.maxAdContentRating != null && this.maxAdContentRating != "") {
            RequestConfiguration requestConfiguration = MobileAds.getRequestConfiguration()
                    .toBuilder()
                    .setMaxAdContentRating(this.maxAdContentRating)
                    .build();
            MobileAds.setRequestConfiguration(requestConfiguration);
        }
    }


    /**
     * Returns AdRequest object constructed considering the extras.
     *
     * @return AdRequest object
     */
    private AdRequest getAdRequest() {
        AdRequest.Builder adBuilder = new AdRequest.Builder();
        AdRequest adRequest;
        if (!this.isForChildDirectedTreatment && extras != null) {
            adBuilder.addNetworkExtrasBundle(AdMobAdapter.class, extras);
        }

        adRequest = adBuilder.build();
        return adRequest;
    }

    /**
     * To Initializes AdMob on a background thread to improve performance.
     */
    @UsedByGodot
    public void initializeOnBackgroundThread() {
        new Thread(() -> {
            MobileAds.initialize(activity, new OnInitializationCompleteListener() {
                @Override
                public void onInitializationComplete(InitializationStatus initializationStatus) {
                    emitSignal("on_admob_initialized");
                }
            });
        }).start();
    }

    /* Rewarded Video
     * ********************************************************************** */

    /**
     * Load a Rewarded Video
     *
     * @param id AdMod Rewarded video ID
     */
    @UsedByGodot
    public void loadRewardedVideo(final String id) {
        activity.runOnUiThread(() -> {
            rewardedVideo = new RewardedVideo(activity, new RewardedVideoListener() {
                @Override
                public void onRewardedVideoLoaded() {
                    emitSignal("on_rewarded_video_ad_loaded");
                }

                @Override
                public void onRewardedVideoFailedToLoad(int errorCode) {
                    emitSignal("on_rewarded_video_ad_failed_to_load", errorCode);
                }

                @Override
                public void onRewardedVideoOpened() {
                    emitSignal("on_rewarded_video_ad_opened");
                }

                @Override
                public void onRewardedVideoClosed() {
                    emitSignal("on_rewarded_video_ad_closed");
                }

                @Override
                public void onRewarded(String type, int amount) {
                    emitSignal("on_rewarded", type, amount);
                }

                @Override
                public void onRewardedClicked() {
                    emitSignal("on_rewarded_clicked");
                }

                @Override
                public void onRewardedAdImpression() {
                    emitSignal("on_rewarded_impression");
                }
            });
            rewardedVideo.load(id, getAdRequest());
        });
    }

    /**
     * Show a Rewarded Video
     */
    @UsedByGodot
    public void showRewardedVideo() {
        activity.runOnUiThread(() -> {
            if (rewardedVideo == null) {
                return;
            }
            rewardedVideo.show();
        });
    }

    /* Rewarded Interstitial
     * ********************************************************************** */

    /**
     * Load a Rewarded Interstitial
     *
     * @param id AdMod Rewarded interstitial ID
     */
    @UsedByGodot
    public void loadRewardedInterstitial(final String id) {
        activity.runOnUiThread(() -> {
            rewardedInterstitial = new RewardedInterstitial(activity, new RewardedInterstitialListener() {
                @Override
                public void onRewardedInterstitialLoaded() {
                    emitSignal("on_rewarded_interstitial_ad_loaded");
                }

                @Override
                public void onRewardedInterstitialOpened() {
                    emitSignal("on_rewarded_interstitial_ad_opened");
                }

                @Override
                public void onRewardedInterstitialClosed() {
                    emitSignal("on_rewarded_interstitial_ad_closed");
                }

                @Override
                public void onRewardedInterstitialFailedToLoad(int errorCode) {
                    emitSignal("on_rewarded_interstitial_ad_failed_to_load", errorCode);
                }

                @Override
                public void onRewardedInterstitialFailedToShow(int errorCode) {
                    emitSignal("on_rewarded_interstitial_ad_failed_to_show", errorCode);
                }

                @Override
                public void onRewarded(String type, int amount) {
                    emitSignal("on_rewarded", type, amount);
                }

                @Override
                public void onRewardedClicked() {
                    emitSignal("on_rewarded_clicked");
                }

                @Override
                public void onRewardedAdImpression() {
                    emitSignal("on_rewarded_impression");
                }
            });
            rewardedInterstitial.load(id, getAdRequest());
        });
    }

    /**
     * Show a Rewarded Interstitial
     */
    @UsedByGodot
    public void showRewardedInterstitial() {
        activity.runOnUiThread(() -> {
            if (rewardedInterstitial == null) {
                return;
            }
            rewardedInterstitial.show();
        });
    }


    /* Banner
     * ********************************************************************** */

    /**
     * Load a banner
     *
     * @param id      AdMod Banner ID
     * @param isOnTop To made the banner top or bottom
     */
    @UsedByGodot
    public void loadBanner(final String id, final boolean isOnTop, final String bannerSize) {
        activity.runOnUiThread(() -> {
            if (banner != null) banner.remove();
            banner = new Banner(id, getAdRequest(), activity, new BannerListener() {
                @Override
                public void onBannerLoaded() {
                    emitSignal("on_admob_ad_loaded");
                }

                @Override
                public void onBannerFailedToLoad(int errorCode) {
                    emitSignal("on_admob_banner_failed_to_load", errorCode);
                }
            }, isOnTop, layout, bannerSize);
        });
    }

    /**
     * Show the banner
     */
    @UsedByGodot
    public void showBanner() {
        activity.runOnUiThread(() -> {
            if (banner != null) {
                banner.show();
            }
        });
    }

    /**
     * Resize the banner
     * @param isOnTop To made the banner top or bottom
     */
    @UsedByGodot
    public void move(final boolean isOnTop) {
        activity.runOnUiThread(() -> {
            if (banner != null) {
                banner.move(isOnTop);
            }
        });
    }

    /**
     * Resize the banner
     */
    @UsedByGodot
    public void resize() {
        activity.runOnUiThread(() -> {
            if (banner != null) {
                banner.resize();
            }
        });
    }


    /**
     * Hide the banner
     */
    @UsedByGodot
    public void hideBanner() {
        activity.runOnUiThread(() -> {
            if (banner != null) {
                banner.hide();
            }
        });
    }

    /**
     * Get the banner width
     *
     * @return int Banner width
     */
    @UsedByGodot
    public int getBannerWidth() {
        if (banner != null) {
            return banner.getWidth();
        }
        return 0;
    }

    /**
     * Get the banner height
     *
     * @return int Banner height
     */
    @UsedByGodot
    public int getBannerHeight() {
        if (banner != null) {
            return banner.getHeight();
        }
        return 0;
    }

    /* Interstitial
     * ********************************************************************** */

    /**
     * Load a interstitial
     *
     * @param id AdMod Interstitial ID
     */
    @UsedByGodot
    public void loadInterstitial(final String id) {
        activity.runOnUiThread(() -> interstitial = new Interstitial(id, getAdRequest(), activity, new InterstitialListener() {
            @Override
            public void onInterstitialLoaded() {
                emitSignal("on_interstitial_loaded");
            }

            @Override
            public void onInterstitialFailedToLoad(int errorCode) {
                emitSignal("on_interstitial_failed_to_load", errorCode);
            }

            @Override
            public void onInterstitialOpened() {
                // Not Implemented
                emitSignal("on_interstitial_opened");
            }

            @Override
            public void onInterstitialClosed() {
                emitSignal("on_interstitial_close");
            }

            @Override
            public void onInterstitialClicked() {
                emitSignal("on_interstitial_clicked");
            }

            @Override
            public void onInterstitialImpression() {
                emitSignal("on_interstitial_impression");
            }
        }));
    }

    /**
     * Show the interstitial
     */
    @UsedByGodot
    public void showInterstitial() {
        activity.runOnUiThread(() -> {
            if (interstitial != null) {
                interstitial.show();
            }
        });
    }

    /* ConsentInformation
     * ********************************************************************** */
    @UsedByGodot
    public void requestConsentInfoUpdate(final boolean testingConsent){

        activity.runOnUiThread(() -> cmp = new CMP(activity,
                testingConsent,
                testingConsent ? getAdMobDeviceId() : "",
                new CMPListener() {
            @Override
            public void onConsentInfoUpdateSuccess() {
                emitSignal("on_consent_info_update_success");
            }

            @Override
            public void onConsentInfoUpdateFailure(int errorCode, String errorMessage) {
                emitSignal("on_consent_info_update_failure", errorCode, errorMessage);
            }

            @Override
            public void onAppCanRequestAds(int consentStatus) {
                emitSignal("on_app_can_request_ads", consentStatus);
            }
        }));
    }

    @UsedByGodot
    public void resetConsentInformation(){
        Log.w("godot", "Removing consent: ");
        if(cmp != null) {
            cmp.resetConsentInformation();
        }
    }

    /* Utils
     * ********************************************************************** */

    /**
     * Generate MD5 for the deviceID
     *
     * @param s The string to generate de MD5
     * @return String The MD5 generated
     */
    private String md5(final String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < messageDigest.length; i++) {
                String h = Integer.toHexString(0xFF & messageDigest[i]);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            //Logger.logStackTrace(TAG,e);
        }
        return "";
    }

    /**
     * Get the Device ID for AdMob
     *
     * @return String Device ID
     */
    private String getAdMobDeviceId() {
        @SuppressLint("HardwareIds") String android_id = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceId = md5(android_id).toUpperCase(Locale.US);
        return deviceId;
    }

}
