package top.icheyy.webrtcdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import okhttp3.OkHttpClient;

public class MainActivity extends Activity implements WebRTCClient.RtcListener {

    private static final String TAG = "MainActivity";

    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;

    private EditText mEtName;
    private EditText mEtCalleeName;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;

    public static Handler mHandler = new Handler();
    private String mSocketAddress;
    private WebRTCClient pcClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        mSocketAddress = "https://" + "laravue.xyz:3000";
//        mSocketAddress = "https://" + "laravue.xyz";
        mSocketAddress = "http://" + "call.icheyy.top:8081";
        initViews();

        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "run in VideoRendererGui.setView");
                init();
            }
        });

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

    }

    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters pcParams = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);

        pcClient = new WebRTCClient(this, mSocketAddress, getIOOptions(), pcParams, VideoRendererGui.getEGLContext(), this);
    }

    private void initViews() {
        mEtName = (EditText) findViewById(R.id.et_name);
        mEtCalleeName = (EditText) findViewById(R.id.et_calleeName);
    }

    public void click2Join(View view) {
        Log.i(TAG, "click2Join: ====================");
        String name = mEtName.getText().toString();
        Log.d(TAG, "click2Join: name:: " + name);
        if (TextUtils.isEmpty(name)) {
            Log.e(TAG, "click2Join: Ooops...this username cannot be empty, please try again");
            return;
        }
        pcClient.setSelfId(name);

        JSONObject msg = new JSONObject();
        try {
            msg.put("event", "join");
            msg.put("name", name);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        pcClient.sendMessage(msg);
        pcClient.removeAllPeers();
        startCam();
        pcClient.addPeer(name, 0);
    }

    public void click2Call(View view) {
        Log.d(TAG, "click2Call: ====================");
        String calleeName = mEtCalleeName.getText().toString();
        Log.d(TAG, "click2Call: calleeName:: " + calleeName);
        pcClient.setConnectedId(calleeName);
        if (TextUtils.isEmpty(calleeName)) {
            Log.e(TAG, "click2Call: Ooops...this username cannot be empty, please try again");
        }

        JSONObject msg = new JSONObject();
        try {
            msg.put("event", "call");
            msg.put("connectedUser", calleeName);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        pcClient.sendMessage(msg);
        pcClient.addPeer(calleeName, 1);
    }

    // 弹出“发送给……”对话框
    public void call(String callId) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, mSocketAddress + callId);
        intent.setType("text/plain");
        startActivityForResult(Intent.createChooser(intent, "Call someone :"), VIDEO_CALL_SENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VIDEO_CALL_SENT) {
            startCam();
        }
    }

    public void startCam() {
        // Camera settings
        pcClient.start("android_test");
    }

    public void click2HangUp(View view) {
        Log.d(TAG, "click2HangUp: ====================");
        JSONObject msg = new JSONObject();
        try {
            msg.put("event", "leave");
            msg.put("connectedUser", pcClient.getSelfId());
        } catch (JSONException e) {
            e.printStackTrace();
        }
//        mSocket.send(msg.toString());
        pcClient.sendMessage(msg);
        pcClient.handleLeave();
//        handleLeave();
    }

    private IO.Options getIOOptions() {
        SSLSocketFactory sslSocketFactory = getSSLSocketFactory();
        X509TrustManager x509TrustManager = getX509TrustManager();
        if (sslSocketFactory == null || x509TrustManager == null) return null;

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                })
                .sslSocketFactory(sslSocketFactory, x509TrustManager)
                .build();

        // default settings for all sockets
        IO.setDefaultOkHttpWebSocketFactory(okHttpClient);
        IO.setDefaultOkHttpCallFactory(okHttpClient);

        // set as an option
        IO.Options opts = new IO.Options();
        opts.callFactory = okHttpClient;
        opts.webSocketFactory = okHttpClient;
        return opts;
    }

    private X509TrustManager getX509TrustManager() {
        TrustManager[] trustManagers = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
            }
            return (X509TrustManager) trustManagers[0];
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SSLSocketFactory getSSLSocketFactory() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(
                    java.security.cert.X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            @Override
            public void checkServerTrusted(
                    java.security.cert.X509Certificate[] chain,
                    String authType) throws CertificateException {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }};

        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return sslContext == null ? null : sslContext.getSocketFactory();
    }

    @Override
    public void onPause() {
        vsv.onPause();
        if (pcClient != null) {
            pcClient.onPause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
        if (pcClient != null) {
            pcClient.onResume();
        }
    }

    @Override
    public void onDestroy() {
        if (pcClient != null) {
            pcClient.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        Log.d(TAG, "onStatusChanged: newStatus:: " + newStatus);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        Log.d(TAG, "onLocalStream");
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
        Log.d(TAG, "onAddRemoteStream");
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType);
    }

    @Override
    public void onRemoveRemoteStream(int endPoint) {
        Log.d(TAG, "onRemoveRemoteStream");
//        VideoRendererGui.remove(remoteRender);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }
}