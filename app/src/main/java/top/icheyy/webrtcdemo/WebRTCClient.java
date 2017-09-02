package top.icheyy.webrtcdemo;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.opengl.EGLContext;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoSource;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class WebRTCClient {
    private final static String TAG = WebRTCClient.class.getCanonicalName();
    private final static int MAX_PEER = 2;
    private boolean[] endPoints = new boolean[MAX_PEER];
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private VideoSource videoSource;
    private RtcListener mListener;
    private io.socket.client.Socket mSocket;
    private String mSelfId, mConnectedId;

    /**
     * Implement this interface to be notified of events.
     */
    public interface RtcListener {
        void onStatusChanged(String newStatus);

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream, int endPoint);

        void onRemoveRemoteStream(int endPoint);
    }

    public void sendMessage(String msg) {
        mSocket.send(msg);
    }

    public void sendMessage(JSONObject jsonObject) {
        if (jsonObject == null) return;
        Log.i(TAG, "sendMessage: " + jsonObject.toString());
        mSocket.send(jsonObject.toString());
    }

    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;
        private String id;
        private int endPoint;

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {// createOffer/createAnswer成功回调此方法
            if (sdp == null) return;
            Log.d(TAG, "onCreateSuccess: sdp.description:: \n" + sdp.description);
            Log.i(TAG, "onCreateSuccess: sdp.type.canonicalForm():: " + sdp.type.canonicalForm());

            try {
                JSONObject payload = new JSONObject();
                payload.put("type", sdp.type.canonicalForm());
                payload.put("sdp", sdp.description);

                JSONObject msg = new JSONObject();
                msg.put("event", sdp.type.canonicalForm());
                msg.put("connectedUser", mConnectedId);
                msg.put(sdp.type.canonicalForm(), payload);
                sendMessage(msg);
                pc.setLocalDescription(Peer.this, sdp);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {
            Log.e(TAG, "onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.i(TAG, "onIceConnectionChange: id:: " + id);
            Log.d(TAG, "onIceConnectionChange: " + iceConnectionState);
            if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                if (peers != null) {
                    removePeer(id);
                }
                if (mListener != null) {
                    mListener.onStatusChanged(id + " DISCONNECTED");
                }
            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            if (candidate == null) return;
            Log.d(TAG, "onIceCandidate: \ncandidate.sdpMLineIndex:: " + candidate.sdpMLineIndex +
                    "\ncandidate.sdpMid:: " + candidate.sdpMid);
            Log.d(TAG, "onIceCandidate: candidate.sdp:: \n" + candidate.sdp);

            try {
                JSONObject payload = new JSONObject();
                payload.put("sdpMLineIndex", candidate.sdpMLineIndex);
                payload.put("sdpMid", candidate.sdpMid);
                payload.put("candidate", candidate.sdp);

                JSONObject msg = new JSONObject();
                msg.put("event", "candidate");
                msg.put("connectedUser", mConnectedId);
                msg.put("candidate", payload);
                sendMessage(msg);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream " + mediaStream.label());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream, endPoint + 1);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.label());
            removePeer(id);
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
        }

        @Override
        public void onRenegotiationNeeded() {
        }

        public Peer(String id, int endPoint) {
            Log.d(TAG, "new Peer: " + id + " " + endPoint);
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.id = id;
            this.endPoint = endPoint;

            Log.d(TAG, "Peer: localMS:: " + localMS);
            pc.addStream(localMS);

            if (mListener != null) {
                mListener.onStatusChanged(id + " CONNECTING");
            }
        }

        @Override
        public String toString() {
            return "Peer{pc: " + pc + ", id: " + id + ", endPoint: " + endPoint + "}";
        }
    }

    public Peer addPeer(String id, int endPoint) {
        Peer peer = new Peer(id, endPoint);
        peers.put(id, peer);

        endPoints[endPoint] = true;
        return peer;
    }

    public void removePeer(String id) {
        Peer peer = peers.get(id);
        if (peer == null) return;
        mListener.onRemoveRemoteStream(peer.endPoint);
//        peer.pc.close();
        peers.remove(peer.id);
        endPoints[peer.endPoint] = false;
    }

    public void removeAllPeers() {
//        for (Map.Entry<String, Peer> entry : peers.entrySet()) {
//            String id = entry.getKey();
//            Peer peer = peers.get(id);
////            mListener.onRemoveRemoteStream(peer.endPoint);
//            peer.pc.close();
//            endPoints[peer.endPoint] = false;
//        }
        peers.clear();
    }

    private Handler mHandler = MainActivity.mHandler;
    private Context mContext;

    public WebRTCClient(RtcListener listener, String host, io.socket.client.IO.Options options,
                        PeerConnectionParameters params, EGLContext mEGLContext, final Context context) {
        Log.i(TAG, ">>>>>>> WebRTCClient: host:: " + host);
        mContext = context;
        mListener = listener;
        pcParams = params;
        PeerConnectionFactory.initializeAndroidGlobals(listener, //上下文，可自定义监听
                true,//是否初始化音频
                true,//是否初始化视频
                params.videoCodecHwAcceleration,//是否支持硬件加速
                mEGLContext);//是否支持硬件渲染
        factory = new PeerConnectionFactory();

        try {
//            mSocket = IO.socket(socketAddress);
            mSocket = io.socket.client.IO.socket(host, options);
            mSocket.on("message", onMessage);
            mSocket.connect();
            Log.d(TAG, "onCreate: mSocket.connect() finish");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: mSocket.connected():: " + mSocket.connected());
                Toast.makeText(context, "Socket connect: " + mSocket.connected(), Toast.LENGTH_SHORT).show();
            }
        }, 2000);

        iceServers.add(new PeerConnection.IceServer("turn:call.icheyy.top:3478"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    private Map<String, Boolean> mAllUsers;

    private io.socket.emitter.Emitter.Listener onMessage = new io.socket.emitter.Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            JSONObject data = (JSONObject) args[0];
            Log.d(TAG, "call in onMessage: data:: \n" + data.toString());

            try {
                String event = (String) data.get("event");
                if (TextUtils.equals("show", event)) {
                    handleShow(data);
                } else if (TextUtils.equals("join", event)) {
                    handleJoin(data);
                } else if (TextUtils.equals("call", event)) {
                    handleCall(data);
                } else if (TextUtils.equals("accept", event)) {
                    handleAccept(data);
                } else if (TextUtils.equals("offer", event)) {
                    handleOffer(data);
                } else if (TextUtils.equals("candidate", event)) {
                    handleCandidate(data);
                } else if (TextUtils.equals("msg", event)) {
                    handleMsg(data);
                } else if (TextUtils.equals("answer", event)) {
                    handleAnswer(data);
                } else if (TextUtils.equals("leave", event)) {
                    handleLeave();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    private void handleShow(JSONObject data) throws JSONException {
        JSONObject allUsers = data.getJSONObject("allUsers");
        Iterator<String> keys = allUsers.keys();
        if (mAllUsers == null) {
            mAllUsers = new HashMap<>();
        }
        while (keys.hasNext()) {
            String key = keys.next();
            mAllUsers.put(key, (Boolean) allUsers.get(key));
        }
        Log.i(TAG, "handleShow: " + mAllUsers);
    }

    private void handleJoin(JSONObject data) throws JSONException {
        boolean isSuccess = (boolean) data.get("success");

        Log.d(TAG, "handleJoin: isSuccess:: " + isSuccess);
        if (!isSuccess) {
            final String message = (String) data.get("message");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void handleCall(JSONObject data) throws JSONException {
        final String name = (String) data.get("name");
        Log.d(TAG, "handleCall: name:: " + name);
        mConnectedId = name;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                AlertDialog dialog = new AlertDialog.Builder(mContext)
                        .setTitle("视频邀请")
                        .setMessage("来自 " + name + " 的视频邀请，是否接受？")
                        .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                JSONObject msg = new JSONObject();
                                try {
                                    msg.put("event", "accept");
                                    msg.put("connectedUser", mSelfId);
                                    msg.put("accept", true);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG, "onClick: sendAccept:: " + msg.toString());
                                mSocket.send(msg.toString());
                                dialog.cancel();
                            }
                        })
                        .setNegativeButton("Reject", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                JSONObject msg = new JSONObject();
                                try {
                                    msg.put("event", "accept");
                                    msg.put("connectedUser", mSelfId);
                                    msg.put("accept", false);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG, "onClick: sendReject:: " + msg.toString());
                                mSocket.send(msg.toString());
                                dialog.cancel();
                            }
                        }).create();
                dialog.show();
            }
        });
    }

    private void handleAccept(JSONObject data) throws JSONException {
        boolean isAccept = (boolean) data.get("accept");
        Log.d(TAG, "handleAccept: isAccept:: " + isAccept);
        if (isAccept) {
            Log.d(TAG, "handleAccept: peers:: " + peers);
            if (!peers.containsKey(mConnectedId)) {
                addPeer(mConnectedId, 1);
            }
            Peer peer = peers.get(mConnectedId);
            Log.i(TAG, "handleAccept: mConnectedId:: " + mConnectedId);
            Log.d(TAG, "handleAccept: peer:: " + peer);
            Log.d(TAG, "handleAccept: peerConn:: " + peer.pc);
            peer.pc.createOffer(peer, pcConstraints);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, "对方已拒绝", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void handleOffer(JSONObject data) throws JSONException {
        String name = (String) data.get("name");
        JSONObject offer = (JSONObject) data.get("offer");
        Log.d(TAG, "handleOffer: name:: " + name);
        mConnectedId = name;
        Peer peer = peers.get(name);
        SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(offer.getString("type")),
                offer.getString("sdp")
        );
        peer.pc.setRemoteDescription(peer, sdp);
        peer.pc.createAnswer(peer, pcConstraints);
    }

    private void handleCandidate(JSONObject data) throws JSONException {
        JSONObject candidate = (JSONObject) data.get("candidate");
        Log.d(TAG, "handleCandidate: candidate:: " + candidate);

        Peer peer = peers.get(mConnectedId);
        peer.pc.addIceCandidate(new IceCandidate(candidate.getString("sdpMid"),
                candidate.getInt("sdpMLineIndex"), candidate.getString("candidate")));
    }

    private void handleMsg(JSONObject data) throws JSONException {
        String message = (String) data.get("message");
        Log.i(TAG, "handleMsg: message:: " + message);
    }

    private void handleAnswer(JSONObject data) throws JSONException {
        JSONObject answer = (JSONObject) data.get("answer");
        Log.d(TAG, "handleAnswer: answer:: " + answer);
        Peer peer = peers.get(mConnectedId);
        SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(answer.getString("type")),
                answer.getString("sdp")
        );
        peer.pc.setRemoteDescription(peer, sdp);
    }

    public void handleLeave() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, "Chat finished! ", Toast.LENGTH_SHORT).show();
            }
        });

        if (mListener != null) {
            mListener.onRemoveRemoteStream(1);
        }

        removeAllPeers();
//        mListener = null;
        mConnectedId = null;

//        alert("通话已结束");
//        connectedUser = null;
//        this.remote_video = "";
//        peerConn.close();
//        peerConn.onicecandidate = null;
//        peerConn.onaddstream = null;
//        if (peerConn.signalingState == 'closed') {
//            this.initCreate();
//        }
    }

    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
        if (videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
        if (videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void onDestroy() {
        for (Peer peer : peers.values()) {
            peer.pc.dispose();
        }
        if (videoSource != null) {
            videoSource.dispose();
        }
        factory.dispose();
        if (mSocket != null) {
            mSocket.disconnect();
            mSocket.close();
        }
    }

    private int findEndPoint() {
        for (int i = 0; i < MAX_PEER; i++) if (!endPoints[i]) return i;
        return MAX_PEER;
    }

    /**
     * Start the client.
     * <p>
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     *
     * @param name client name
     */
    public void start(String name) {
        setCamera();
    }

    private void setCamera() {
        localMS = factory.createLocalMediaStream("ARDAMS");
        Log.i(TAG, "setCamera: localMS:: " + localMS);
        if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            VideoCapturer videoCapturer = getVideoCapturer();
            Log.d(TAG, "setCamera: videoCapturer:: " + videoCapturer);
            videoSource = factory.createVideoSource(videoCapturer, videoConstraints);
            localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        }

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

        mListener.onLocalStream(localMS);
    }

    private VideoCapturer getVideoCapturer() {
        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        Log.d(TAG, "getVideoCapturer: frontCameraDeviceName:: " + frontCameraDeviceName);
        //权限没开，则此处报错 E/VideoCapturerAndroid: InitStatics failed
        return VideoCapturerAndroid.create(frontCameraDeviceName);
    }

    public String getSelfId() {
        return mSelfId;
    }

    public void setSelfId(String selfId) {
        mSelfId = selfId;
    }

    public void setConnectedId(String connectedId) {
        mConnectedId = connectedId;
    }
}
