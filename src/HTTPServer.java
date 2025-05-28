package src;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;




public class HTTPServer {
    //Helper method to get content type
    private static String getContentType(String filePath) {
        if (filePath.endsWith(".html")) return "text/html";
        if (filePath.endsWith(".css")) return "text/css";
        if (filePath.endsWith(".js")) return "application/javascript";
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) return "image/jpeg";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }
    private static void sendErrorPage(OutputStream out, String message) throws IOException {
        String html = "<html><body><h1>Error</h1><p>" + message + "</p><a href=\"/search\">Back</a></body></html>";
        out.write("HTTP/1.1 400 Bad Request\r\n".getBytes());
        out.write("Content-Type: text/html\r\n\r\n".getBytes());
        out.write(html.getBytes());
        out.flush();
    }
    

     public static class ClientHandler extends Thread{
        private Socket clientSocket;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;

        }
        private void log(String method, String path, int statusCode) {
            String logEntry = String.format("[%s] %s %s -> %d",
                java.time.LocalDateTime.now(),
                method,
                path,
                statusCode
            );
            System.out.println(logEntry);
        }
        
        
          
        public void run() {
            try  {
                System.out.println("Debug: got new message " + clientSocket.toString());
                System.out.println("Handling thread: " + Thread.currentThread().getName());

                //get the input 
                InputStreamReader isr = new InputStreamReader(clientSocket.getInputStream());
                BufferedReader br = new BufferedReader(isr);
                //request
                StringBuilder request = new StringBuilder();
                String line; // Temp variable called line that holds one line at a time of our message

                //reads line by line of the request and adds it to string request
                int contentLength = 0;


                line = br.readLine();
                while (line != null && !line.isBlank()) {
                    request.append(line).append("\r\n");
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                    }
                    line = br.readLine();
                }

                //  Now parse method and resource
                String firstLine = request.toString().split("\n")[0];
                String[] parts = firstLine.split(" ");
                String method = parts[0];
                String resource = parts[1];
                System.out.println(resource);

                // Now you can check for POST
                if (method.equals("POST") && resource.equals("/search")) {
                    char[] bodyChars = new char[contentLength];
                    int read = 0;
                    while (read < contentLength) {
                        int r = br.read(bodyChars, read, contentLength - read);
                        if (r == -1) break;
                        read += r;
                    }
                    String requestBody = new String(bodyChars);
                    System.out.println("Raw POST body: [" + requestBody + "]");
                        System.out.println("Content-Length: " + contentLength);
                        System.out.println("Request Body: [" + requestBody + "]");
                    
                    
                        // Step 3: Parse form data
                        String[] params = requestBody.split("&");
                        String searchTerm = null;
                        for (String param : params) {
                            String[] keyValue = param.split("=", 2);
                            if (keyValue.length == 2 && keyValue[0].equals("name")) {
                                searchTerm = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                                break;
                            }
                        }
                    
                        if (searchTerm == null || searchTerm.isBlank()) {
                            sendErrorPage(clientSocket.getOutputStream(), "Missing search term.");
                            log(method, resource, 400);
                            return;
                        }
                    
                        System.out.println("User searched for: " + searchTerm);
                    
                        // Step 4: Query SWAPI.tech
                        try {
                            
                            StringBuilder htmlResponse = new StringBuilder();
                            htmlResponse.append("<html><head>");
                            htmlResponse.append("<link rel=\"stylesheet\" href=\"/public/styles.css\">");
                            htmlResponse.append("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />");
                            htmlResponse.append("<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />");
                            htmlResponse.append("<link href=\"https://fonts.googleapis.com/css2?family=Lobster&family=Press+Start+2P&display=swap\" rel=\"stylesheet\" />");
                            htmlResponse.append("</head><body>");
                            htmlResponse.append("<h1>Search Results for '").append(searchTerm).append("'</h1>");

                            boolean found = false;
                            for (int page = 1; page <= 9; page++) {
                                String pageUrl = "https://www.swapi.tech/api/people?page=" + page + "&limit=10";
                                URL url = URI.create(pageUrl).toURL();
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("GET");
                            
                                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                StringBuilder responseBuilder = new StringBuilder();
                                
                                while ((line = reader.readLine()) != null) {
                                    responseBuilder.append(line);
                                }
                                reader.close();
                            JSONObject jsonResponse = new JSONObject(responseBuilder.toString());
                            JSONArray results = jsonResponse.getJSONArray("results");
                        
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject result = results.getJSONObject(i);
                                String name = result.getString("name");

                                if (name.equalsIgnoreCase(searchTerm)) {
                                    found = true;

                                    // Step 7: Fetch full character data from result URL
                                    URL detailUrl = new URL(result.getString("url"));
                                    HttpURLConnection detailConn = (HttpURLConnection) detailUrl.openConnection();
                                    detailConn.setRequestMethod("GET");

                                    BufferedReader detailReader = new BufferedReader(new InputStreamReader(detailConn.getInputStream()));
                                    StringBuilder detailResponse = new StringBuilder();
                                    
                                    while ((line = detailReader.readLine()) != null) {
                                        detailResponse.append(line);
                                    }
                                    detailReader.close();

                                    JSONObject detailJson = new JSONObject(detailResponse.toString());
                                    JSONObject properties = detailJson.getJSONObject("result").getJSONObject("properties");

                                    htmlResponse.append("<h2>").append(properties.getString("name")).append("</h2>");
                                    htmlResponse.append("<p>Height: ").append(properties.getString("height")).append("</p>");
                                    htmlResponse.append("<p>Mass: ").append(properties.getString("mass")).append("</p>");
                                    htmlResponse.append("<p>Hair Color: ").append(properties.getString("hair_color")).append("</p>");
                                    htmlResponse.append("<p>Skin Color: ").append(properties.getString("skin_color")).append("</p>");
                                    htmlResponse.append("<p>Eye Color: ").append(properties.getString("eye_color")).append("</p>");
                                    htmlResponse.append("<p>Birth Year: ").append(properties.getString("birth_year")).append("</p>");
                                    htmlResponse.append("<p>Gender: ").append(properties.getString("gender")).append("</p>");
                                    break;
                                }
                            }

                               if (found) break;
                        }
                        if (!found) {
                            htmlResponse.append("<p>No character found with name: ").append(searchTerm).append("</p>");
                        }

                            htmlResponse.append("<br><a href=\"/search\">Back to Search</a>");
                            htmlResponse.append("</body></html>");

                            // Step 8: Send response
                            OutputStream clientOutput = clientSocket.getOutputStream();
                            clientOutput.write("HTTP/1.1 200 OK\r\n".getBytes());
                            clientOutput.write("Content-Type: text/html\r\n".getBytes());
                            clientOutput.write("Connection: close\r\n\r\n".getBytes());
                            clientOutput.write(htmlResponse.toString().getBytes(StandardCharsets.UTF_8));
                            clientOutput.flush();

                            log(method, resource, 200);

                            } catch (Exception e) {
                                e.printStackTrace();
                                sendErrorPage(clientSocket.getOutputStream(), "API error: " + e.getMessage());
                                log(method, resource, 500);
                            }
                }
            
                    


                    //YODA
                 else if (resource.equals("/yoda")) {
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    String html = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "<html><head>" +
                                    "<link rel=\"stylesheet\" href=\"/public/styles.css\">" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />" +
                                    "<link href=\"https://fonts.googleapis.com/css2?family=Lobster&family=Press+Start+2P&display=swap\" rel=\"stylesheet\" />" +
                                    "</head>" +
                                    "<body class=\"character-page\">" +
                                    "<h1>Master Yoda</h1>" +
                                    "<img src=\"/public/yoda.jpg\" alt=\"Yoda\" width=\"500\"><br>" +
                                    "<audio controls>" +
                                    "<source src=\"/audio/onlyafulltrained.mp3\" type=\"audio/mpeg\">" +
                                    "Your browser does not support the audio element." +
                                    "</audio>" +
                                    "<li><a href=\"/home\">Back to Home</a></li>" +
                                    "</body></html>";
                    clientOutput.write(html.getBytes());
                    clientOutput.flush();
                    log(method, resource, 200);

                //signals second resource to the image that is handled
                } else if (resource.equals("/images/yoda.jpg")) {
                    FileInputStream image = new FileInputStream("yoda.jpg");
                    byte[] imageBytes = image.readAllBytes();
                    image.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: image/jpeg\r\n" +
                                        "Content-Length: " + imageBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(imageBytes);
                    clientOutput.flush();
                    log(method, resource, 200);
                //audio playback handling
                }else if (resource.equals("/audio/onlyafulltrained.mp3")) {
                    FileInputStream audio = new FileInputStream("audio/onlyafulltrained.mp3");
                    byte[] audioBytes = audio.readAllBytes();
                    audio.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: audio/mpeg\r\n" +
                                        "Content-Length: " + audioBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(audioBytes);
                    clientOutput.flush();
                    log(method, resource, 200);
                    //DARTH VADER
                }else if (resource.equals("/darthvader")) {
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    String html = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "<html><head>" +
                                    "<link rel=\"stylesheet\" href=\"/public/styles.css\">" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />" +
                                    "<link href=\"https://fonts.googleapis.com/css2?family=Lobster&family=Press+Start+2P&display=swap\" rel=\"stylesheet\" />" +
                                    "</head>" +
                                    "<body class=\"character-page\">" +
                                    "<h1>Darth Vader (Anakin Skywalker)</h1>" +
                                    "<img src=\"/public/darthvader.jpg\" alt=\"Darth Vader\" width=\"500\"><br>" +
                                    "<audio controls>" +
                                    "<source src=\"/audio/darthvader.mp3\" type=\"audio/mpeg\">" +
                                    "Your browser does not support the audio element." +
                                    "</audio>" +
                                    "<li><a href=\"/home\">Back to Home</a></li>" +
                                    "</body></html>";

;
                    clientOutput.write(html.getBytes());
                    clientOutput.flush();
                    log(method, resource, 200);

                //signals second resource to the image that is handled
                } else if (resource.equals("/images/darthvader.jpg")) {
                    FileInputStream image = new FileInputStream("darthvader.jpg");
                    byte[] imageBytes = image.readAllBytes();
                    image.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: image/jpeg\r\n" +
                                        "Content-Length: " + imageBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(imageBytes);
                    clientOutput.flush();
                //audio playback handling
                }else if (resource.equals("/audio/darthvader.mp3")) {
                    FileInputStream audio = new FileInputStream("audio/darthvader.mp3");
                    byte[] audioBytes = audio.readAllBytes();
                    audio.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: audio/mpeg\r\n" +
                                        "Content-Length: " + audioBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(audioBytes);
                    clientOutput.flush();
                    log(method, resource, 200);
                //CHEWBACCA
                }else if (resource.equals("/chewbacca")) {
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    String html = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "<html><head>" +
                                    "<link rel=\"stylesheet\" href=\"/public/styles.css\">" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />" +
                                    "<link href=\"https://fonts.googleapis.com/css2?family=Lobster&family=Press+Start+2P&display=swap\" rel=\"stylesheet\" />" +
                                    "</head>" +
                                    "<body class=\"character-page\">" +
                                    "<h1>Chewbacca</h1>" +
                                    "<img src=\"/public/chewbacca.jpg\" alt=\"Chewbacca\" width=\"500\"><br>" +
                                    "<audio controls>" +
                                    "<source src=\"/audio/chewbacca.mp3\" type=\"audio/mpeg\">" +
                                    "Your browser does not support the audio element." +
                                    "</audio>" +
                                    "<li><a href=\"/home\">Back to Home</a></li>" +
                                    "</body></html>";

;
                    clientOutput.write(html.getBytes());
                    clientOutput.flush();
                    log(method, resource, 200);

                //signals second resource to the image that is handled
                } else if (resource.equals("/images/chewbacca.jpg")) {
                    FileInputStream image = new FileInputStream("chewbacca.jpg");
                    byte[] imageBytes = image.readAllBytes();
                    image.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: image/jpeg\r\n" +
                                        "Content-Length: " + imageBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(imageBytes);
                    clientOutput.flush();
                    log(method, resource, 200);
                //audio playback handling
                }else if (resource.equals("/audio/chewbacca.mp3")) {
                    FileInputStream audio = new FileInputStream("audio/chewbacca.mp3");
                    byte[] audioBytes = audio.readAllBytes();
                    audio.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: audio/mpeg\r\n" +
                                        "Content-Length: " + audioBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(audioBytes);
                    clientOutput.flush();
                    log(method, resource, 200);
                //DROIDS
                }else if (resource.equals("/droids")) {
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    String html = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html\r\n" +
                                    "Connection: close\r\n\r\n" +
                                    "<html><head>" +
                                    "<link rel=\"stylesheet\" href=\"/public/styles.css\">" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />" +
                                    "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />" +
                                    "<link href=\"https://fonts.googleapis.com/css2?family=Lobster&family=Press+Start+2P&display=swap\" rel=\"stylesheet\" />" +
                                    "</head>" +
                                    "<body class=\"character-page\">" +
                                    "<h1>R2D2 & C3PO</h1>" +
                                    "<img src=\"/public/droids.jpg\" alt=\"Droids\" width=\"500\"><br>" +
                                    "<audio controls>" +
                                    "<source src=\"/audio/droids.mp3\" type=\"audio/mpeg\">" +
                                    "Your browser does not support the audio element." +
                                    "</audio>" +
                                    "<li><a href=\"/home\">Back to Home</a></li>" +
                                    "</body></html>";

;
                    clientOutput.write(html.getBytes());
                    clientOutput.flush();
                    log(method, resource, 200);

                //signals second resource to the image that is handled
                } else if (resource.equals("/images/droids.jpg")) {
                    FileInputStream image = new FileInputStream("droids.jpg");
                    byte[] imageBytes = image.readAllBytes();
                    image.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: image/jpeg\r\n" +
                                        "Content-Length: " + imageBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(imageBytes);
                    clientOutput.flush();
                //audio playback handling
                }else if (resource.equals("/audio/droids.mp3")) {
                    FileInputStream audio = new FileInputStream("audio/droids.mp3");
                    byte[] audioBytes = audio.readAllBytes();
                    audio.close();
                
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: audio/mpeg\r\n" +
                                        "Content-Length: " + audioBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n").getBytes());
                    clientOutput.write(audioBytes);
                    clientOutput.flush();
                    log(method, resource, 200);
                
                }else if (resource.equals("/home")) {
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    String html = "HTTP/1.1 200 OK\r\n" +
              "Content-Type: text/html\r\n" +
              "Connection: close\r\n\r\n" +
              "<!DOCTYPE html>" +
              "<html lang=\"en\">" +
              "<head>" +
              "<meta charset=\"UTF-8\" />" +
              "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />" +
              "<meta http-equiv=\"X-UA-Compatible\" content=\"ie=edge\" />" +
              "<title>Star Wars Server Home</title>" +
              "<link rel=\"stylesheet\" href=\"/public/styles.css\" />" +
              "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />" +
              "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />" +
              "<link href=\"https://fonts.googleapis.com/css2?family=Lobster&family=Press+Start+2P&display=swap\" rel=\"stylesheet\" />" +
              "</head>" +
              "<body>" +
              "<div class=\"hero-image\">" +
              "<div class=\"hero-text\">" +
              "<h1>Star Wars Server Home</h1>" +
              "<p>Explore the galaxy right from your browser! Search for your favorite Star Wars characters, view detailed profiles powered by the SWAPI API, and enjoy custom pages for icons like Yoda, Darth Vader, and Chewbacca — all hosted on a custom-built HTTP server.</p>" +
              "<p> By: Dillon O'Bourke </p>" + 
              "</div>" +
              "</div>" +
              "<div class=\"link-section\">" +
              "<a class=\"nav-link\" href=\"/yoda\">Master Yoda</a>" +
              "<p>Wisdom from the legendary Jedi Master.</p>" +
              "<a class=\"nav-link\" href=\"/darthvader\">Darth Vader (Anakin Skywalker)</a>" +
              "<p>Learn about the fallen hero turned Sith Lord.</p>" +
              "<a class=\"nav-link\" href=\"/chewbacca\">Chewbacca</a>" +
              "<p>Meet the loyal Wookiee warrior from Kashyyyk.</p>" +
              "<a class=\"nav-link\" href=\"/droids\">R2D2 & C3PO</a>" +
              "<p>The iconic droid duo that’s seen it all.</p>" +
              "<a class=\"nav-link\" href=\"/random\">RANDOM</a>" +
              "<p>Feeling lucky? Discover a random character.</p>" +
              "<a class=\"nav-link\" href=\"/search\">Search with API</a>" +
              "<p>Search any Star Wars character using live data.</p>" +
              "</div>" +
              "<div class=\"empty\">yo</div>" +
              "</body></html>";

                    clientOutput.write(html.getBytes());
                    clientOutput.flush();
                    log(method, resource, 200);
                }
                else if (resource.equals("/random")) {
                    OutputStream clientOutput = clientSocket.getOutputStream();
                
                    // List of available character routes
                    String[] characters = { "/yoda", "/darthvader", "/chewbacca", "/droids" };
                
                    // Pick a random one
                    int randomIndex = (int) (Math.random() * characters.length);
                    String randomRoute = characters[randomIndex];
                
                    // Send HTTP redirect
                    String response = "HTTP/1.1 302 Found\r\n" +
                                      "Location: " + randomRoute + "\r\n" +
                                      "Connection: close\r\n\r\n";
                
                    clientOutput.write(response.getBytes());
                    clientOutput.flush();
                    log(method, resource, 302);
                }
                // FILE SERVING
                else if (resource.startsWith("/public/")) {
                    String filePath = "public" + resource.substring("/public".length());
                    File file = new File(filePath);

                    if (file.exists() && file.isFile()) {
                        String contentType = getContentType(filePath);
                        byte[] fileBytes = Files.readAllBytes(file.toPath());

                        OutputStream clientOutput = clientSocket.getOutputStream();
                        String headers = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Type: " + contentType + "\r\n" +
                                        "Content-Length: " + fileBytes.length + "\r\n" +
                                        "Connection: close\r\n\r\n";
                        clientOutput.write(headers.getBytes());
                        clientOutput.write(fileBytes);
                        clientOutput.flush();
                        log(method, resource, 200);
                    } else {
                        // Send 404 if file doesn't exist
                        OutputStream clientOutput = clientSocket.getOutputStream();
                        clientOutput.write(("HTTP/1.1 404 Not Found\r\n\r\nFile not found").getBytes());
                        clientOutput.flush();
                        log(method, resource, 404);
                    }
                }

                
                // send the HTML  page
                else if (method.equals("GET") && resource.equals("/search")) {
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n").getBytes());
                    clientOutput.write("Content-Type: text/html\r\n".getBytes());
                    clientOutput.write("Connection: close\r\n\r\n".getBytes());
                    clientOutput.write(("<!DOCTYPE html>\n" + //
                                                "<html>\n" + //
                                                "<head>\n" + //
                                                "  <title>Star Wars Character Search</title>\n" + //
                                                "  <link rel=\"stylesheet\" href=\"/public/styles.css\"> <!-- Optional CSS -->\n" + //
                                                "<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />" +
                                                "<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />" +
                                                "<link href=\"https://fonts.googleapis.com/css2?family=Lobster&family=Press+Start+2P&display=swap\" rel=\"stylesheet\" />" +
                                                "</head>\n" + //
                                                "<body>\n" + //
                                                "  <h1>Search Star Wars Characters</h1>\n" + //
                                                "  <form action=\"/search\" method=\"POST\">\n" + //
                                                "    <label for=\"name\">Enter character name:</label><br>\n" + //
                                                "    <input type=\"text\" id=\"name\" name=\"name\" required>\n" + //
                                                "    <button type=\"submit\">Search</button>\n" + //
                                                "  </form>\n" + //
                                                "  <br>\n" + //
                                                "  <a href=\"/home\">Back to Home</a>\n" + //
                                                "</body>\n" + //
                                                "</html>").getBytes());
                    clientOutput.flush();
                    log(method,resource,200);


                }
                                    
                else if (resource.equals("/hello")) {
                    //send back Hello World
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 200 OK\r\n").getBytes());
                    clientOutput.write(("\r\n").getBytes());
                    clientOutput.write(("Hello World").getBytes());
                    clientOutput.flush();

                } else {
                    //error 404 handling
                    OutputStream clientOutput = clientSocket.getOutputStream();
                    clientOutput.write(("HTTP/1.1 404 Not Found\r\n").getBytes());
                    clientOutput.write(("\r\n").getBytes());
                    clientOutput.write(("This is not the path you are looking for").getBytes());
                    clientOutput.flush();
                    log(method, resource, 404);
                

                }
                
                clientSocket.close();
                
                
            
        } catch (IOException e){
            e.printStackTrace();
        }
    }
    
    
}
        public static void main(String[] args) throws Exception{
            System.out.println("Hello World");    
            int port = 8080;
            
    
            try (ServerSocket serverSocket = new ServerSocket(8080)) {
                //tells console if server started
                System.out.println("Server started on http://localhost:" + port);
                
                //continuous for as long the server is up
                while (true) {
                    Socket client = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(client);
                    handler.start(); 
                        }                                   
                    }
                    
                }
    }
  