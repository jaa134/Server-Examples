import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

public class DummyServer {

    public static void main(String[] args) {
        int portNumber = Integer.parseInt(args[0]);

        try (
            ServerSocket serverSocket = new ServerSocket(portNumber);
        ) {
            String time = getTime();
            String serverAddress = serverSocket.getLocalSocketAddress().toString();
            System.out.printf("[%s] Server address:   %s\n", time, serverAddress);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientConnectionHandler handler = new ClientConnectionHandler(clientSocket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getTime() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");
        return dtf.format(LocalDateTime.now());
    }

    private static class ClientConnectionHandler implements Runnable {

        private static final String OUTPUT_HEADERS = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html\r\n" + 
            "Content-Length: ";
        private static final String OUTPUT_END_OF_HEADERS = "\r\n\r\n";
        private static final String OUTPUT = "<html>Responsed</html>\r\n";

        private Socket clientSocket;

        public ClientConnectionHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }
        
        public void run() {
            String time = getTime();
            String remoteAddress = clientSocket.getRemoteSocketAddress().toString();
            String localAddress = clientSocket.getLocalSocketAddress().toString();
            System.out.printf("[%s] Found connection:   remote=%s   local=%s\n", time, remoteAddress, localAddress);
            System.out.flush();

            try (
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(
                        new BufferedOutputStream(clientSocket.getOutputStream()), "UTF-8")
                    );
            ) {
                String line = null;
                while ((line = br.readLine()) != null && !line.isEmpty()) {
                    System.out.println(line);
                }
                out.write(OUTPUT_HEADERS + OUTPUT.length() + OUTPUT_END_OF_HEADERS + OUTPUT);
                out.flush();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } 
    }

}