//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the
import java.io.*;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.TimeZone;

public class MyWebServer{

    //------------------------------------ Socket I/O methods ----------------------------------------------------

    private static String inFromClient(Socket connectionSocket) throws IOException
        /* Gets input from the client and returns it as a string using bufferedreader to read the
           input stream from the socket and stringbuilder to build a string from the input
        */
    {
        //connection to sockets' input
        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        //builder to add to the string
        StringBuilder request = new StringBuilder();

        //keep reading lines and appending them to the builder until you read a blank line which signifies
        //the end of the request
        String clientSentence;
        while(!((clientSentence = inFromClient.readLine()).isBlank()))
        {
            request.append(clientSentence).append("\r\n");
        }
        //convert string builder to a string
        return request.toString();
    }

    private static void outToClient(Socket connectionSocket, HTTPResponse response)
        /* Connects to socket output and sends a response formulated by the request parser and handler.
        using data output stream to connect to socket output and output response
        needs to check if response is a valid GET command and write file to output if it is
        */
    {
        try
        {
            //connect to sockets' output stream
            DataOutputStream outputStream = new DataOutputStream(connectionSocket.getOutputStream());
            //output responses' contents to the stream
            //if an error message causes response to lack any of this content, the instance will be
            //empty and therefore nothing will be outputted to the stream
            outputStream.writeBytes(response.statusLine);
            outputStream.writeBytes(response.date);
            outputStream.writeBytes(response.server);
            outputStream.writeBytes(response.lastModified);
            outputStream.writeBytes(response.contentLength);
            outputStream.writeBytes("\r\n");

            if(response.isGet)
            {
                //create an input stream for reading the file
                FileInputStream fileInput = new FileInputStream(response.file);
                int data;
                byte[] buffer = new byte[8192];
                //will keep grabbing bytes from the file until there are none left
                while ((data = fileInput.read(buffer)) != -1)
                {
                    //outputs bytes of the file out to the socket
                    outputStream.write(buffer, 0, data);
                }
                fileInput.close();
            }
            //if there was an error, write error message to the output
            else if(!response.statusLine.equals("HTTP/1.1 200 OK"))
            {
                outputStream.write(response.statusLine.getBytes());
                outputStream.flush();
            }
            outputStream.close();


        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //-------------------------------------- Main --------------------------------------------------------------


    public static void main(String[] args) {
        //default port number
        int port = 8080;

        //first argument should be port number
        port = Integer.parseInt(args[0]);


        if(!(args[1].endsWith("/evaluationWeb")&&new File(args[1]).isDirectory()))
        //second argument should be valid directory leading to evaluationWeb folder
        {
            System.exit(1);
        }
        try
        {
            //HTTP welcome socket
            ServerSocket welcomeSocket = new ServerSocket(port);

            while(true)
            //wait for connection to welcome socket from client
            {
                Socket clientSocket = welcomeSocket.accept();

                //once connection is established, get request and respond to it
                HTTPRequest request = new HTTPRequest(inFromClient(clientSocket),args[1]);
                HTTPResponse response = new HTTPResponse(request,"Gus' server");
                outToClient(clientSocket,response);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

//-------------------------------------- HTTP parser and handler classes ----------------------------------------

class HTTPRequest
    /* 2.Implement the section of the program that will receive the entire request from the socket and parse the
    information that you need to fulfill the GET or HEAD request.*/
{
    Date ifModSince=null;
    String command="";
    String path="";
    boolean badRequest = false;
    boolean notImplemented = false;

    public HTTPRequest(String request, String rootPath)
        /* 2.1.takes the string request you pull off the socket and tokenizes the request string and takes
        out and stores the important information (the Command, the Path, the If-Modified-Since date (if one
        is specified)).*/
    {
        //splits string line by line
        String[] requestLines = request.split(System.getProperty("line.separator"));

        //if command, path, or version is missing, it's a bad request
        StringTokenizer firstLine = new StringTokenizer(requestLines[0]);
        if(firstLine.countTokens()==3)
        {
            this.setCommand(firstLine.nextToken());
            this.setPath(firstLine.nextToken(),rootPath);
            this.setIfModSince(requestLines);
        }
        else
        {
            badRequest = true;
        }
    }

    //------------------------------------ Parsing methods -------------------------------------------------------------
    public void setCommand(String str)
    //makes sure command is either head or get
    //set boolean not implemented to true if otherwise
    {
        str = str.toUpperCase();

        //check if command is either get or head
        if(!(str.equals("GET")||str.equals("HEAD")))
        {
            this.notImplemented = true;
        }
        else
        {
            //stores command from first line
            this.command = str;
            command = command.toUpperCase();
        }
    }
    public void setPath(String str, String rootPath)
    {
        this.path = rootPath;
        //uses URI to get and return truncated path(if an absolute uri), bad request if not uri syntax
         /* 2.2.At this point, you can truncate the path (if necessary), concatenate it to the
        end of the root directory, and add “index.html” if the path leads to a directory. */
        try
        {
            URI uri = new URI(str);
            this.path += uri.getPath();
        }
        catch (URISyntaxException e) {
            badRequest = true;
        }

        //if file leads to a directory, then append it with index html
        if(new File(this.path).isDirectory())
        {
            if(this.path.endsWith("/")){this.path += "index.html";}
            else{this.path+="/index.html";}
        }
    }

    public void setIfModSince(String[] requestLines)
    {
        //for parsing ifmodsince date
        SimpleDateFormat HTTPDateFormat = new SimpleDateFormat("EEE MMM d hh:mm:ss zzz yyyy");
        HTTPDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        //looks through header line for if modified since and retrieves string if it is there
        //parse string into a date
        for(int i=0; i < requestLines.length;i++)
        {
            if(requestLines[i].startsWith("If-Modified-Since:"))
            {
                //ifmodsince line with the if-modified-since string at the start and with whitespace on both ends
                // eliminated. bad request if date not in correct format
                try
                {
                    String ifModSinceStr = requestLines[i].substring(18).trim();
                    this.ifModSince = HTTPDateFormat.parse(ifModSinceStr);
                }
                catch (Exception e) {
                    badRequest = true;
                }
            }
        }
    }

    public void print()
    //for testing
    {
        SimpleDateFormat DateFormat = new SimpleDateFormat("EEE MMM d hh:mm:ss zzz yyyy");
        DateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        System.out.println("Command: "+this.command);
        System.out.println("path: "+this.path);
        System.out.println("ifModSince: "+ DateFormat.format(ifModSince));
        System.out.println("Bad Request: " + badRequest);
        System.out.println("Not implemented: " + notImplemented);
    }
}

class HTTPResponse
    /*.Implement the part of the server that takes some action based upon the information within the particular
    HTTP request object. This could be thought of as a request handler*/
{
    //We need boolean variables for the last 2 error types which the response class will check for
    //response message needs a date,sever,and status line and needs a last modified and content length if the
    //file exists
    //we need a boolean to know if the file is going to be needed in the response
    //Lastly, we need an error message for the output stream if there is an error
    boolean notModified = false;
    boolean notFound = false;

    boolean isGet = false;
    String date="";
    String server="";
    String lastModified="";
    String contentLength="";
    String statusLine="";
    File file;


    public HTTPResponse(HTTPRequest request, String server)
    {
        //sets server which is arbitrary
        this.server = "Server: " + server+"\r\n";
        //check if command is get
        if(request.command.equals("GET")){this.isGet = true;}
        //sets file, content length, date, and modified since
        setFileInfo(request.path, request.ifModSince);
        //sets status line based on if there were errors or not. Important that this is last.
        setStatusLine(request.badRequest,request.notImplemented,this.notFound,this.notModified);
        
    }

    //---------------------------------- Handling methods --------------------------------------------------------
    public void setFileInfo(String path, Date IfModSince)
        //3.1. take a path and a modified since and will attempt find the appropriate file.
    {

        SimpleDateFormat HTTPDateFormat = new SimpleDateFormat("EEE MMM d hh:mm:ss zzz yyyy");
        HTTPDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        //set current date while using the httpdateformat
        this.date = "Date: " + HTTPDateFormat.format(Calendar.getInstance().getTime())+"\r\n";

        //check if file exists and if it does, set the content length and last modified instances
        file = new File(path);
        if(file.exists())
        {
            if(IfModSince != null)
            //check if there even was an ifmodsince in the request
            {
                if(IfModSince.after(new Date(file.lastModified())))
                //check that the file has not been modified since the ifmodsince specification
                {
                    notModified =true;
                }

            }
            //set content length and last modified if the file exists
            this.contentLength = "Content-Length: " + file.length() + "\r\n";
            this.lastModified = "Last-Modified: "+HTTPDateFormat.format(file.lastModified())+"\r\n";
        }
        else
        //if file doesnt exist, file not found is true
        {
            this.notFound = true;
        }

    }
    public void setStatusLine(boolean badR, boolean notI, boolean notF, boolean notM)
    //set the status line based on if there were errors in the request and what kind
    {
        if(badR)
        {
            this.statusLine = "HTTP/1.1 400 BAD REQUEST";
            this.contentLength = "Content-Length: " + this.statusLine.getBytes().length+"\r\n";
        }
        else if (notI)
        {
            this.statusLine = "HTTP/1.1 501 NOT IMPLEMENTED";
            this.contentLength = "Content-Length: " + this.statusLine.getBytes().length+"\r\n";
        }
        else if (notF)
        {
            this.statusLine = "HTTP/1.1 404 NOT FOUND";
            this.contentLength = "Content-Length: " + this.statusLine.getBytes().length+"\r\n";
        }
        else if (notM)
        {
            this.statusLine = "HTTP/1.1 304 NOT MODIFIED";
            this.contentLength = "Content-Length: " + this.statusLine.getBytes().length+"\r\n";
        }
        else
        {
            this.statusLine = "HTTP/1.1 200 OK";
        }
        this.statusLine += "\r\n";
    }

    public void print()
    {
        System.out.print(this.statusLine);
        System.out.print(this.date);
        System.out.print(this.server);
        System.out.print(this.lastModified);
        System.out.print(this.contentLength);
        System.out.print("\r\n");
        System.out.print("FileExists?: " + this.file.exists());
    }

}



