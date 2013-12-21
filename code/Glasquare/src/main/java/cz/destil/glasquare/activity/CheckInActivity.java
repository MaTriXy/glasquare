package cz.destil.glasquare.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.glass.media.Sounds;

import java.io.File;

import butterknife.InjectView;
import cz.destil.glasquare.R;
import cz.destil.glasquare.api.Api;
import cz.destil.glasquare.api.Auth;
import cz.destil.glasquare.api.CheckIns;
import cz.destil.glasquare.api.Photos;
import cz.destil.glasquare.util.IntentUtils;
import cz.destil.glasquare.util.LocationUtils;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

/**
 * Base activity which performs a check-in.
 *
 * @author David 'Destil' Vavra (david@vavra.me)
 */
public class CheckInActivity extends BaseActivity {

    public static String EXTRA_VENUE_ID = "venue_id";
    @InjectView(R.id.result)
    TextView vResult;
    @InjectView(R.id.primary_notification)
    TextView vPrimaryNotification;
    @InjectView(R.id.secondary_notification)
    TextView vSecondaryNotification;
    @InjectView(R.id.progress)
    ProgressBar vProgress;
    String mCheckInId = null;

    public static void call(Activity activity, String venueId) {
        Intent intent = new Intent(activity, CheckInActivity.class);
        intent.putExtra(EXTRA_VENUE_ID, venueId);
        activity.startActivity(intent);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_check_in;
    }

    @Override
    protected void loadData() {
        String venueId = getIntent().getStringExtra(EXTRA_VENUE_ID);
        String ll = LocationUtils.getLatLon();
        String token = Auth.getToken();
        showProgress(R.string.checking_in);
        Api.get().create(CheckIns.class).add(token, venueId, ll, new Callback<CheckIns.CheckInResponse>() {
            @Override
            public void success(CheckIns.CheckInResponse checkInResponse, Response response) {
                mCheckInId = checkInResponse.getCheckInId();
                showSuccess(R.string.checked_in);
                if (checkInResponse.getPrimaryNotification() != null) {
                    vPrimaryNotification.setVisibility(View.VISIBLE);
                    vPrimaryNotification.setText(checkInResponse.getPrimaryNotification());
                }
                if (checkInResponse.getSecondaryNotification() != null) {
                    vSecondaryNotification.setVisibility(View.VISIBLE);
                    vSecondaryNotification.setText(checkInResponse.getSecondaryNotification());
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (!Auth.handle(CheckInActivity.this, retrofitError)) {
                    showError(R.string.error_please_try_again);
                }
            }
        });
    }

    @Override
    protected void onTap() {
        if (mCheckInId != null) {
            openOptionsMenu();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.check_in, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_photo:
                IntentUtils.takePicture(this);
                return true;
            case R.id.menu_comment:
                IntentUtils.startSpeechRecognition(this);
                return true;
            case R.id.menu_twitter:
                shareTo("twitter");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String text = IntentUtils.processSpeechRecognitionResult(requestCode, resultCode, data);
        if (text != null) {
            addComment(text);
        } else {
            IntentUtils.processTakePictureResult(this, requestCode, resultCode, data, new IntentUtils.OnPictureReadyListener() {
                @Override
                public void onPathKnown() {
                    showProgress(R.string.adding_photo);
                }

                @Override
                public void onPictureReady(File image) {
                    addPhoto(image);
                }
            });
        }
    }

    private void addComment(String text) {
        String token = Auth.getToken();
        showProgress(R.string.adding_comment);
        Api.get().create(CheckIns.class).addComment(token, mCheckInId, text, new Callback<CheckIns.CheckInResponse>() {
            @Override
            public void success(CheckIns.CheckInResponse checkInResponse, Response response) {
                showSuccess(R.string.comment_added);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                showError(R.string.error_please_try_again);
            }
        });
    }

    private void addPhoto(File image) {
        String token = Auth.getToken();
        TypedFile typedFile = new TypedFile("image/jpeg", image);
        Api.get().create(Photos.class).add(token, mCheckInId, typedFile, new Callback<Photos.PhotoAddResponse>() {
            @Override
            public void success(Photos.PhotoAddResponse photoAddResponse, Response response) {
                showSuccess(R.string.photo_added);
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                showError(R.string.error_please_try_again);
            }
        });
    }

    private void shareTo(final String socialNetwork) {
        String venueId = getIntent().getStringExtra(EXTRA_VENUE_ID);
        String ll = LocationUtils.getLatLon();
        String token = Auth.getToken();
        if (socialNetwork.equals("twitter")) {
            showProgress(R.string.sharing_to_twitter);
        }
        Api.get().create(CheckIns.class).add(token, venueId, ll, socialNetwork, new Callback<CheckIns.CheckInResponse>() {
            @Override
            public void success(CheckIns.CheckInResponse checkInResponse, Response response) {
                mCheckInId = checkInResponse.getCheckInId();
                if (socialNetwork.equals("twitter")) {
                    showSuccess(R.string.shared_to_twitter);
                }
            }

            @Override
            public void failure(RetrofitError retrofitError) {
                if (!Auth.handle(CheckInActivity.this, retrofitError)) {
                    showError(R.string.error_please_try_again);
                }
            }
        });
    }

    protected void showProgress(int resourceId) {
        vResult.setText(resourceId);
        hideIcon();
        vProgress.setVisibility(View.VISIBLE);
        vPrimaryNotification.setVisibility(View.GONE);
        vSecondaryNotification.setVisibility(View.GONE);
    }

    protected void showSuccess(int resourceId) {
        vResult.setText(resourceId);
        vProgress.setVisibility(View.GONE);
        vPrimaryNotification.setVisibility(View.GONE);
        vSecondaryNotification.setVisibility(View.GONE);
        showIcon();
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audio.playSoundEffect(Sounds.SUCCESS);
    }

    protected void showError(int resourceId) {
        vResult.setText(resourceId);
        vProgress.setVisibility(View.GONE);
        vPrimaryNotification.setVisibility(View.GONE);
        vSecondaryNotification.setVisibility(View.GONE);
        hideIcon();
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audio.playSoundEffect(Sounds.ERROR);
    }

    private void showIcon() {
        vResult.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.ic_menu_checked_in), null, null, null);
    }

    private void hideIcon() {
        vResult.setCompoundDrawables(null, null, null, null);
    }
}
