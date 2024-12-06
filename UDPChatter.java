import java.io.*;
import java.awt.*;
import java.net.*;
import javax.swing.*;
import java.awt.event.*;
import java.util.concurrent.*;
// Joe T.Schwarz (C)
public class UDPChatter extends JFrame implements ActionListener {

    protected String isMe;
    protected JTextField line;
    protected JTextArea taLog;
    protected UDPChatter me = this;
    protected JComboBox<String> jcb;
    protected ExecutorService pool = Executors.newFixedThreadPool(2);
    // JavaSWING - the showcase...
    public UDPChatter(String title) {
        super(title);
        // open UDP socket
        Authenticate au = new Authenticate(me);
        if (au.isCanceled()) System.exit(0);
        
        taLog = new JTextArea(16, 42);
        taLog.setBackground(Color.lightGray);
        taLog.setEditable(false);
        
        jcb = new JComboBox<String>(new String[] {"ALL"});
        jcb.setPreferredSize(new Dimension(100, 25));
        
        line = new JTextField(30);
        line.setEditable(true);
        line.setBackground(Color.green);
        line.addActionListener(this);
        
        try {
            dSoc = new DatagramSocket( );
            hostIP = InetAddress.getByName(host);
            StringBuilder sb = new StringBuilder();
            if (au.isLogin()) sb.append("login!"+au.getEncryptedID( )+"!"+au.getEncryptedPW());
            else sb.append("register!"+au.getEncryptedID()+"!"+au.getEncryptedPW());
            dSoc.send(new DatagramPacket(sb.toString().getBytes(), sb.length(), hostIP, port));
            TimeUnit.MILLISECONDS.sleep(100);
            byte[] buf = new byte[256];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            dSoc.receive(pkt);
            buf = pkt.getData();
            int le = pkt.getLength();
            String msg = (new String(buf, 0, le)).trim();
            if (!isWelcome(msg)) {
              JOptionPane.showMessageDialog(me, msg); 
              shutdown(false);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(me,"Weird thing happened. Fail to create User DatagramSocket"); 
            e.printStackTrace();
            shutdown(false);
        }
        JPanel pnl = new JPanel();
        pnl.setBackground(Color.pink);        
        JButton butGO = new JButton("SEND");
        butGO.setForeground(Color.black);
        butGO.setFocusable(false);
        butGO.setFont(new Font("Times",Font.BOLD, 11));
        butGO.addActionListener(this);
        pnl.add(butGO);

        JPanel lPanel = new JPanel();
        lPanel.add(new JLabel("I chat"));
        lPanel.add(line);

        lPanel.add( new JLabel("with"));
        lPanel.add(jcb);
           
        JPanel logPanel = new JPanel();
        JScrollPane jTextArea = new JScrollPane(taLog); 
        logPanel.setBorder(BorderFactory.createTitledBorder("From UDPServer..."));
        taLog.setWrapStyleWord(true);
        jTextArea.setAutoscrolls(true);
        taLog.setLineWrap(true);
        logPanel.add(jTextArea);
       
        add("North",lPanel);
        add("Center",logPanel);
        add("South",pnl);
          
        addWindowListener(
            new WindowAdapter() {
                public void windowClosing(WindowEvent we){
                    shutdown(true);
                }
            }
        );
        setBounds(150, 50, 550, 450);
        setVisible(true);
        // Client listening Thread
        pool.execute(() -> {
          byte[] buf = new byte[65001];
          DatagramPacket pkt = new DatagramPacket(buf, buf.length);
          while (done) try { // waiting for incoming packet              
            dSoc.receive(pkt);
            buf = pkt.getData();
            int le = pkt.getLength();
            String msg = (new String(buf, 0, le)).trim();
            if (msg.endsWith("closed!") || "ID is null ???".equals(msg)) { // UDPForum down
              JOptionPane.showMessageDialog(me, msg);
              TimeUnit.MILLISECONDS.sleep(100);
              shutdown(false);
            }
            if (!isWelcome(msg)) {
              if (msg.startsWith("<>ALL")) update(msg);
              else taLog.append(msg+"\n");
            }
          } catch (Exception e) {
             if (done) e.printStackTrace();
             shutdown(done);
          }
        });
    }
    //
    private boolean isWelcome(String msg) {
      if (!msg.startsWith("Welcome ")) return false;
      int p = msg.indexOf("<>");
      if (isMe == null) {
        isMe = msg.substring(msg.indexOf("ID: ")+4, p);
        taLog.append(msg.substring(0, p)+"\n");
      }
      update(msg.substring(p+2));
      return true;
    }
    //
    private void update(String msg) {
      String u[] = msg.replace("<>", "").replace(isMe, "").split("!");
      SwingUtilities.invokeLater(() -> {
        jcb.removeActionListener(me);
        jcb.removeAllItems();
        for (int i = 0; i < u.length; ++i) 
          if (u[i].length() > 0) jcb.addItem(u[i]);
        jcb.setSelectedIndex(0);
        jcb.addActionListener(me);
      });
    }
    // banned or closed
    private volatile boolean done = true;
    public void shutdown(boolean bye) {
        done = false;
        if (dSoc != null) try {
          if (bye) {
            byte[] myMSG = "Bye UDPForum\n".getBytes();
            dSoc.send(new DatagramPacket(myMSG, myMSG.length, hostIP, port));
            TimeUnit.MILLISECONDS.sleep(100);
          }
          dSoc.close( );
        } catch (Exception e) { }
        dSoc = null;
        pool.shutdownNow();
        System.exit(0);
    }
    // Button Send
    private InetAddress hostIP;
    private DatagramSocket dSoc;
    public void actionPerformed(ActionEvent ev) {
        try { // get the selected partner
            String msg = null, txt = line.getText( ).trim();
            String who = (String)jcb.getSelectedItem();
            msg = who+"!"+txt;
            if (!"ALL".equals(who)) taLog.append(isMe+" chats with "+who+": "+txt+"\n");
            else if (jcb.getItemCount() == 1) taLog.append("Start to chat with everyone: "+txt+"\n");
            //
            dSoc.send(new DatagramPacket(msg.getBytes(),msg.length(), hostIP, port));
            jcb.removeActionListener(me);
            jcb.setSelectedIndex(0);
            jcb.addActionListener(me);
            line.setText("");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    protected static int port;
    protected static String host;
    public static void main(String[] args) {
        if (args.length == 1) {
            try {
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
                port = args[0].indexOf(':');
                host = args[0].substring(0, port++);
                port = Integer.parseInt(args[0].substring(port, args[0].length()));
                UDPChatter uc = new UDPChatter("Joe's UDPChatter for UDPServer");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Usage: java UDPChatter HostName:Port");
                System.exit(0);
            }
        } else {
            JOptionPane.showMessageDialog( null, "Usage: java UDPChatter HostName:Port");
            System.exit(0);
        }
    }
}
