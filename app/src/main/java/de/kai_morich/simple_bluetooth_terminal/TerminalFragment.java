package de.kai_morich.simple_bluetooth_terminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.ActionProvider;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private Button XA;
    private Button XS;
    private Button YA;
    private Button YS;
    private Button ZA;
    private Button ZS;
    private Button Update;

    private TextView ValueX;
    private TextView ValueY;
    private TextView ValueZ;
    private TextView ValueA;
    private TextView ValueB;
    private TextView ValueC;
    private TextView stateMessage;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        View view = inflater.inflate(R.layout.hiwin_arm_control, container, false);
//        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
//        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
//        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

//        sendText = view.findViewById(R.id.send_text);
//        hexWatcher = new TextUtil.HexWatcher(sendText);
//        hexWatcher.enable(hexEnabled);
//        sendText.addTextChangedListener(hexWatcher);
//        sendText.setHint(hexEnabled ? "HEX mode" : "");

        ValueX = (TextView) view.findViewById(R.id.textViewNowPosition0);
        ValueY = (TextView) view.findViewById(R.id.textViewNowPosition1);
        ValueZ = (TextView) view.findViewById(R.id.textViewNowPosition2);
        ValueA = (TextView) view.findViewById(R.id.textViewNowPosition3);
        ValueB = (TextView) view.findViewById(R.id.textViewNowPosition4);
        ValueC = (TextView) view.findViewById(R.id.textViewNowPosition5);
        stateMessage = (TextView) view.findViewById(R.id.state);

        XA = (Button) view.findViewById(R.id.buttonXA);
        XS = (Button) view.findViewById(R.id.buttonXS);
        YA = (Button) view.findViewById(R.id.buttonYA);
        YS = (Button) view.findViewById(R.id.buttonYS);
        ZA = (Button) view.findViewById(R.id.buttonZA);
        ZS = (Button) view.findViewById(R.id.buttonZS);
        Update = (Button) view.findViewById(R.id.buttonUpdate);

        XA.setOnClickListener(v -> send("X"));
        XS.setOnClickListener(v -> send("x"));
        YA.setOnClickListener(v -> send("Y"));
        YS.setOnClickListener(v -> send("y"));
        ZA.setOnClickListener(v -> send("Z"));
        ZS.setOnClickListener(v -> send("z"));
        Update.setOnClickListener(v -> send("u"));

//        View sendBtn = view.findViewById(R.id.send_btn);
//        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
//            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
//            sendText.setText("");
            hexWatcher.enable(hexEnabled);
//            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(byte[] data) {
        if(hexEnabled) {
//            receiveText.append(TextUtil.toHexString(data) + '\n');
        } else {
            try {
                stateMessage.setText(Integer.toString(data.length) +" ; " +TextUtil.toHexString(data));

                int offset = 0;
                switch (data.length)
                {
                    case 15:
                    case 14:
                    case 13:
                        int c = (((data[data.length - 13] & 0xff) << 8) | (data[data.length - 12]&0xff));
                        ValueC.setText(Integer.toString(c));
                    case 12:
                    case 11:
                        int b = (((data[data.length - 11] & 0xff) << 8) | (data[data.length - 10]&0xff));
                        ValueB.setText(Integer.toString(b));
                    case 10:
                    case 9:
                        int a = (((data[data.length - 9] & 0xff) << 8) | (data[data.length - 8]&0xff));
                        ValueA.setText(Integer.toString(a));
                    case 8:
                    case 7:
                        int z = (((data[data.length - 7] & 0xff) << 8) | (data[data.length - 6]&0xff));
                        ValueZ.setText(Integer.toString(z));
                    case 6:
                    case 5:
                        int y = (((data[data.length - 5] & 0xff) << 8) | (data[data.length - 4]&0xff));
                        ValueY.setText(Integer.toString(y));
                    case 4:
                    case 3:
                        int x = (((data[data.length - 3] & 0xff) << 8) | (data[data.length - 2]&0xff));
                        ValueX.setText(Integer.toString(x));
                    default:
                        break;
                }
            }
            catch (Exception e){
//               stateMessage.setText(e.getMessage());
            }
        }
    }

    private void status(String str) {
//        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
//        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        receiveText.append(spn);
//        stateMessage.setText(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
