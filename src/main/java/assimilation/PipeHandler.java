package assimilation;

import arc.struct.Array;
import arc.util.Log;
import arc.util.Time;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.function.Consumer;

public class PipeHandler {
    private String pipeName;
    private RandomAccessFile pipe;

    private HashMap<String, Array<Consumer <String>>> actions = new HashMap<>();

    public PipeHandler(String pipeName){
        this.pipeName = pipeName;
        try {
            this.pipe = new RandomAccessFile(pipeName, "rw");
            Log.info("Connected to pipe " + pipeName);
        } catch (FileNotFoundException e) {
            Log.info("Couldn't find pipe " + pipeName);
        }
    }

    public void write(String message){
        try {
            pipe.write((message + "\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void on(String message, Consumer<String> r){
        if(actions.containsKey(message)){
            actions.get(message).add(r);
        }else{
            actions.put(message, Array.with(r));
        }

    }

    public void beginRead(){
        Log.info("Beginning read");
        new Thread(this::read).start();
    }

    private void fire(String message){
        String[] splt = message.split(";");
        String key = splt[0];
        String value = splt[1];
        actions.get(key).each((r) -> {r.accept(value);});
    }

    private void read() {

        try {
            String line;
            while (null != (line = pipe.readLine())) {
                if(actions.containsKey(line.split(";")[0])){
                    fire(line);
                }else{
                    Log.info("Unknown message \"" + line + "\"");
                }
            }
            Log.info("Finished reading");
        }catch(IOException e){
            e.printStackTrace();

        }
    }

}
