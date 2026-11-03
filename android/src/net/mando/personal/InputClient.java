package net.mando.personal;
import org.json.JSONObject;

interface InputClient {
    boolean isConnected();
    void action(JSONObject data) throws Exception;
    void close();
}
