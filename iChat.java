import java.io.*;
import java.awt.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import java.awt.event.*;
// Joe T. Schwarz
public class iChat extends JFrame implements ActionListener {
	private static final long serialVersionUID = 30L;
  private iChat me = this;
	private Socket soc;
	private String[] list;
	private JTextField line;
	private JTextArea taLog;
	private JComboBox<String> talkers;
  private volatile boolean go = true;
  private String isMe;
  private InputStream inp;
  private OutputStream out;
  //
	public iChat(String title, String hostPort) {
		super(title);
    Properties vp = null;
		try {
				int p = hostPort.indexOf(':');
				String host = hostPort.substring(0, p);
				int port = Integer.parseInt(hostPort.substring(p+1, hostPort.length()));
        soc = new Socket(host, port);
        out = soc.getOutputStream( );
        inp = soc.getInputStream( );
		} catch (Exception e) {
			JOptionPane.showMessageDialog( null, "Invalid "+hostPort+". Expected: hostname:port"); 
			System.exit(0);
		}
		JPanel pnl = new JPanel();
		pnl.setBackground(Color.pink);		
		GridBagLayout gbs = new GridBagLayout();
		pnl.setLayout(new GridBagLayout());
		GridBagConstraints cCenter = new GridBagConstraints();
		cCenter.anchor  = GridBagConstraints.CENTER;
		gbs.setConstraints(pnl,cCenter);
		
		JLabel jlab = new JLabel("  iChat ");
		line = new JTextField();
		line.setEditable(true);
		line.setBackground(Color.green);
		JPanel lPanel = new JPanel();
		lPanel.setLayout(new BorderLayout());
		lPanel.add(jlab, BorderLayout.WEST);
		lPanel.add(line, BorderLayout.CENTER);
		
		taLog = new JTextArea("You speak to a participant or ALL:\n"+
					"1. Write your message then\n"+
					"2. Just pick the ID you want to chat with\n"+
          "3. or ALL to everyone online.\n");
		taLog.setBackground(Color.lightGray);
		taLog.setEditable(false);
		JPanel logPanel = new JPanel();
		JScrollPane jTextArea = new JScrollPane(taLog); 
		logPanel.setBorder(BorderFactory.createTitledBorder("Discussion Protocol..."));
		taLog.setWrapStyleWord(true);
		jTextArea.setAutoscrolls(true);
		taLog.setLineWrap(true);

		logPanel.setLayout(new BorderLayout());
		logPanel.add(jTextArea, BorderLayout.CENTER);
		try {	// login and get iForum-ID
			// verify ID & Password 
			boolean closed = false;
			byte[] bb = new byte[512];
      Authenticate au = new Authenticate(this);
			if (au.isCanceled()) System.exit(0);
			// send iForum encrypted ID and PW
      if (au.isLogin())  out.write((au.getEncryptedID( )+"\n"+au.getEncryptedPW( )).getBytes());
			else out.write(("register\n"+au.getEncryptedID( )+"\n"+au.getEncryptedPW( )).getBytes());
			out.flush( );
			// Read iForum for Permission
			int	n = inp.read(bb);
			closed = (bb[0] == '\n');
      if (bb[0] == '?') JOptionPane.showMessageDialog(this, new String(bb, 1, n-1));
			if (!closed) {
        go = false;
				try {
					inp.close( );
					out.close( );
					soc.close( );
				} catch (Exception ne) { }
				JOptionPane.showMessageDialog(this, "Failed to login Chatroom "+hostPort);
				System.exit(0);
			}
			// own iForum_ID
			isMe = new String(bb, 1, n - 1);
			taLog.append("Your ID is "+isMe);
			
			n = inp.read(bb); // remove own iForum_ID from the ComboBox
			list = ((new String(bb,1,n-1)).replace(isMe+"!","")).split("!");
			talkers = new JComboBox<>(list);
			talkers.setBackground(Color.pink);
			talkers.setFont(new Font("Times",Font.BOLD, 11));
			talkers.addActionListener(this);
      //
      pnl.add(new JLabel("iChat with"));
			pnl.add(talkers);
			//pnl.add(butI);
			     
			add("North",lPanel);
			add("Center",logPanel);
			add("South",pnl);
		  // start Listener
			(new Listener( )).start();	
		} catch (Exception y) {
			JOptionPane.showMessageDialog(this, "Unable to find iForum "+hostPort); 
			System.exit(0);
		}
		addWindowListener(
			new WindowAdapter() {
				public void windowClosing(WindowEvent we){
					actionExit();
				}
			}
		);
		setSize(550, 400);
		setVisible(true);		
	}
	private void forumClosed( ) {
		JOptionPane.showMessageDialog( this, "Sorry! It's curfew. Forum closed!");
		if (go) actionExit( );
	}
  public void actionExit( ) {
   	go = false;
 		try {
 			out.write(0x02); // say Goodbye
 			out.flush( );
      inp.close( );
      out.close( );
      soc.close( );
    } catch (Exception ne) { }
		System.exit(0);
  }
  private class Listener extends Thread {
   	public Listener( ) { }
   	public void run( ) {
   		try {
   			byte[] rep = new byte[65536];  
   			while (go) {
   				int n = inp.read(rep);
					if (n < 0) {
						forumClosed( );
						return;
					}
					if (rep[0] == (byte)0x01) {
            // new iForum Chatter List
            SwingUtilities.invokeLater(() -> {
              String[] nList = ((new String(rep,1,n-1)).replace(isMe+"!","")).split("!");
              talkers.removeActionListener(me);
              talkers.removeAllItems( );
              for (int i = 0; i < nList.length; ++i)talkers.addItem(nList[i]);
              list = new String[nList.length];
              System.arraycopy(nList, 0, list, 0, nList.length);
              talkers.setSelectedIndex(0);
              talkers.addActionListener(me);
            });
					} else {
            String msg = new String(rep, 0, n);
						synchronized(taLog) {
							taLog.append("\n"+msg);
						}
					}
				}
			} catch (Exception x) {
				//x.printStackTrace();
				forumClosed( );
			}
   	}
  }
	public void actionPerformed(ActionEvent ev) {
   		String talk = line.getText( ).trim( );
      if (talk.length() > 0 && talkers.getSelectedIndex() > 0) try {
     		String whom = (String)talkers.getSelectedItem();
        out.write(("<"+whom+">"+talk).getBytes());
        out.flush( );
        //
        synchronized(taLog) {
          taLog.append((whom.charAt(0) != 'A'? "\niChat with "+whom+": \"":
                                               "\niChat with everyone: \"")+
                        talk+"\"");
        }
        talkers.removeActionListener(me);
        talkers.setSelectedIndex(0);
        talkers.addActionListener(me);
      } catch (Exception ex) { }
     	line.setText("");
  }
  public static void main(String... args) throws Exception {
    UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
		if (args.length == 1) {
      new iChat("Joe's iChat", args[0]);
		} else {
			JOptionPane.showMessageDialog( null, "Usage: java iChat HostName:Port"); 
			System.exit(0);
		}
	}
}
