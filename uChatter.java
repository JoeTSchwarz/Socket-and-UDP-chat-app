// Joe T. Schwarz (C)
import java.net.InetAddress;
public class uChatter {
    public uChatter(InetAddress ip, int port) {
        this.port = port;
        this.ip = ip;
    }
    //
    public InetAddress ip;
    public String id;
    public int port;
}