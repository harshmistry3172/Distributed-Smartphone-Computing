package com.example.server;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1;
    ServerSocket serverSocket;
    Thread Thread1 = null;
    TextView tvIP, tvPort;
    TextView tvMessages;
    EditText etMessage;
    Button btnSend;
    Button btnReset;
    public static String SERVER_IP = "";
    public static final int SERVER_PORT = 8080;
    String message;
    int clientCount = 0; // Variable to track connected clients
    Set<Socket> clientSockets = new HashSet<>(); // Set to store active client sockets
    long timeTaken;
    long startTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvIP = findViewById(R.id.tvIP);
        tvPort = findViewById(R.id.tvPort);
        tvMessages = findViewById(R.id.tvMessages);
//        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnReset = findViewById(R.id.btnReser);
        try {
            SERVER_IP = getLocalIpAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        Thread1 = new Thread(new Thread1());
        Thread1.start();

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartApp();
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkAndRequestPermissions()) {
                    String fileName = Environment.getExternalStorageDirectory().getPath() + "/testing.txt"; // Replace with your actual file
                    new SendFileTask().execute(fileName);
                }
            }
        });

    }

    private boolean checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_CODE_STORAGE_PERMISSION);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                btnSend.performClick();
            } else {
                tvMessages.append("Storage permission denied.\n");
            }
        }
    }

    public void restartApp() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        int pendingIntentId = 123456;
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), pendingIntentId, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
        System.exit(0);
    }

    private class SendFileTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            sendFileToClients(params[0]);
            return null;
        }
    }

    private void sendFileToClients(String fileName) {
        startTime = System.currentTimeMillis();
        File file = new File(fileName);
        if (!file.exists()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvMessages.append("File not found: " + fileName + "\n");
                }
            });
            return;
        }

        try {
            // Calculate number of clients and split file accordingly
            int numberOfClients = clientSockets.size();
            List<String> subfileNames = FileSplitter.splitTextFile(fileName, numberOfClients);

            // Iterate over each client socket and send corresponding subfile
            int clientIndex = 0;
            for (Socket clientSocket : clientSockets) {
                String subfileName = subfileNames.get(clientIndex);
                sendSubfile(clientSocket, subfileName);
                clientIndex++;
            }
            long endTime = System.currentTimeMillis();
            timeTaken = endTime - startTime;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendSubfile(Socket clientSocket, String subfileName) throws IOException {
        File file = new File(subfileName);
        if (!file.exists()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvMessages.append("File not found: " + subfileName + "\n");
                }
            });
            return;
        }

        byte[] buffer = new byte[10000];
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);

        long fileSize = file.length();
        DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

        // Send number of files (only 1 file in this case)
        dos.writeInt(1);

        // Send file size
        dos.writeLong(fileSize);

        // Send file name
        dos.writeUTF(file.getName());

        // Send file data
        int bytesRead;
        while ((bytesRead = bis.read(buffer)) > 0) {
            dos.write(buffer, 0, bytesRead);
        }
        dos.flush(); // Flush output stream after sending the file

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvMessages.append("File sent to client: " + clientSocket.getInetAddress().getHostAddress() + "(" + timeTaken + ")ms" + "\n");
            }
        });

        bis.close();
    }

    private String getLocalIpAddress() throws UnknownHostException {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        assert wifiManager != null;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        return InetAddress.getByAddress(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
    }

    private PrintWriter output;
    private BufferedReader input;

    class Thread1 implements Runnable {
        @Override
        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tvMessages.setText("Not connected");
                        tvIP.setText("IP: " + SERVER_IP);
                        tvPort.setText("Port: " + String.valueOf(SERVER_PORT));
                    }
                });

                while (true) {
                    socket = serverSocket.accept();
                    if (clientSockets.add(socket)) {
                        // New client connected
                        updateClientCountUI(clientSockets.size()); // Update UI with client count
                        output = new PrintWriter(socket.getOutputStream(), true);
                        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvMessages.setText("Connected clients: " + clientSockets.size() + "\n"); // Update UI message
                            }
                        });
                        new Thread(new Thread2()).start(); // Pass socket to Thread2
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateClientCountUI(int count) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvMessages.setText("Connected clients: " + count + "\n");
            }
        });
    }

    private class Thread2 implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = input.readLine()) != null) {
                    final String receivedMessage = message;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvMessages.append(receivedMessage + "\n");
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class Thread3 implements Runnable {
        private String message;

        Thread3(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            output.println(message);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvMessages.append("server: " + message + " ");
//                    etMessage.setText("");
                }
            });
        }
    }
}
