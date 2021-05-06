package mage.player.ai.RLAgent;


import java.net.*;
import java.io.*;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.nd4j.shade.wstx.sw.OutputElementBase;
import com.google.gson.*;
import java.lang.NumberFormatException;

public class PyConnection {
    BufferedReader reader;
    InputStreamReader input;
    DataOutputStream dataout;
    BufferedOutputStream buff;
    Socket socket;
    private static final Logger logger = Logger.getLogger(PyConnection.class);
    public PyConnection(int port){
        try {
            socket = new Socket("localhost", port);
            OutputStream output = socket.getOutputStream();
            buff=new BufferedOutputStream(output);
            dataout=new DataOutputStream(buff);
            input = new InputStreamReader(socket.getInputStream());
            reader=new BufferedReader(input);
        }
        catch (UnknownHostException ex) {
            System.out.println("Server not found: " + ex.getMessage());
            System.exit(-1);
        } catch (IOException ex) {

            System.out.println("I/O error in Init: " + ex.getMessage());
            //System.exit(-1);
        }
    }
    public void write(JSONObject repr){
        String message=repr.toString();
        send(message);
    }
    void send(String message){
        try{
            long messageLen=message.length();
            dataout.writeLong(messageLen);
            dataout.writeBytes(message);
            buff.flush();
        }catch (IOException ex) {
            System.out.println("I/O error in send: " + ex.getMessage());
        }
    }
    public void write_hparams(){
        HParams hParams=new HParams();
        Gson gson = new Gson();
        send(gson.toJson(hParams));
    }
    public void write_string(String str){
        send(str);
    }
    int read(){
        String message="";
        try{
            //logger.info("reading");
            message = reader.readLine();
            //logger.info("recieved message: "+message);
            int result=Integer.parseInt(message);
            return result;
        }
        catch (IOException ex) {
            System.out.println("I/O error in read: " + ex.getMessage());
            return -2;
        }
        catch (NumberFormatException ex){
            System.out.println("Terminating due to closed python server" + ex.getMessage());
            return -2;
        }
    }
}
