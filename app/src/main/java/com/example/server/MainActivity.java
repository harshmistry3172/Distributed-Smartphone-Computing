package com.example.server;

import androidx.appcompat.app.AppCompatActivity;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    ServerSocket serverSocket;
    Thread Thread1 = null;
    TextView tvIP, tvPort;
    TextView tvMessages;
    EditText etMessage;
    Button btnSend;
    public static String SERVER_IP = "";
    public static final int SERVER_PORT = 8080;
    String message;
    int clientCount = 0; // Variable to track connected clients
    Set<Socket> clientSockets = new HashSet<>(); // Set to store active client sockets

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvIP = findViewById(R.id.tvIP);
        tvPort = findViewById(R.id.tvPort);
        tvMessages = findViewById(R.id.tvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        try {
            SERVER_IP = getLocalIpAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        Thread1 = new Thread(new Thread1());
        Thread1.start();

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String fileName = "/sdcard/testing.txt"; // Replace with your actual file
                sendFileToClients(fileName);
            }
        });
    }

    private void sendFileToClients(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            tvMessages.append("File not found: " + fileName + "\n");
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendSubfile(Socket clientSocket, String subfileName) throws IOException {
        File file = new File(subfileName);
        if (!file.exists()) {
            tvMessages.append("File not found: " + subfileName + "\n");
            return;
        }

        byte[] buffer = new byte[4096];
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
                tvMessages.append("File sent to client: " + clientSocket.getInetAddress().getHostAddress() + "\n");
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
                tvMessages.setText("Connected clients: " + count);
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
                            tvMessages.append("client: " + receivedMessage + "\n");
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
                    etMessage.setText("");
                }
            });
        }
    }
}
