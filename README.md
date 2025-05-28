# Star Wars HTTP Server 🚀

A custom-built Java HTTP server that serves Star Wars-themed pages, images, and audio. It supports static and dynamic routing, POST search with the SWAPI API, and multithreaded request handling.

## 🚀 Features
- Serves static files (HTML, CSS, images, audio)
- Dynamic routing for multiple Star Wars characters
- `POST` form submission to search characters via SWAPI
- Real-time API integration with pagination and error handling
- **Multithreaded connection handling** using a custom `ClientHandler` class
- Request logging with timestamps, HTTP methods, and status codes
- Clean project structure with `/public`, `/audio`, and `/images` assets


## 🧪 How to Run

Prerequisites
-Java 17+ installed
-Git installed
-Internet connection (used for SWAPI requests)

1. Clone the repository
IN TERMINAL:
type : git clone https://github.com/dillono2277/StarwarsHTTPServer.git

type : cd StarwarsHTTPServer

3. Compile the project
IN TERMINAL:
type : javac -cp .:json-20210307.jar src/HTTPServer.java

5. Run the server
IN TERMINAL:
type : java -cp .:json-20210307.jar src.HTTPServer
🔗 Open your browser and go to:
http://localhost:8080/home

6. To end server, open new terminal, type commands:
   lsof -i :8080
   kill -9 #####
   (5 digit number that shows up)



## Routes to try
| Route                | Description                                                  |
|----------------------|--------------------------------------------------------------|
| `/home`              | Main homepage with intro, nav links, and character blurbs    |
| `/yoda`              | Page for Master Yoda with image and quote audio              |
| `/darthvader`        | Darth Vader profile page with media                          |
| `/chewbacca`         | Chewbacca's page with Wookiee sounds                         |
| `/droids`            | R2-D2 & C-3PO profile with visuals and audio                 |
| `/random`            | Redirects to a random character page                         |
| `/search` (GET)      | Form page to search any character by name                    |
| `/public/styles.css` | CSS styling for all pages                                    |
| `/public/page.html`  | Example of a directly served static HTML file                |

