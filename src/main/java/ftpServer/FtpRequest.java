package ftpServer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
/**
 * MultiThread Ftp Request handler pour écouter des commands de client au
 * serveur
 */
public class FtpRequest extends Thread // Ftp
{
    final static int FS_WAIT_LOGIN = 0;
    final static int FS_WAIT_PASS = 1;
    final static int FS_LOGIN = 2;
    final static int FTYPE_ASCII = 0;
    final static int FTYPE_IMAGE = 1;
    final static int FTRANS_PORT = 0;
    final static int FTRANS_PASV = 1;

    Socket csocket;
    Socket dsocket;
    ServerSocket ssocket;
    int id;
    String cmd = "";
    String param = "";
    String user;
    String remoteHost = " ";
    int remotePort = 0;
    String dir = FtpServer.initDir;
    String rootdir = "/";
    int state = 0;
    String reply;
    PrintWriter out;
    int type = FTYPE_IMAGE;
    String requestfile = "";
    boolean isrest = false;
    int mode = FTRANS_PORT;

    /**
     * parser des commandes en numéro
     */
    int parseInput(String s) //
    {
        int p = 0;
        int i = -1;

        // si il existe des commandes avec paramètre, séparer les dans variable de cmd et param
        p = s.indexOf(" ");
        if (p == -1) {
            cmd = s;
        } else {
            cmd = s.substring(0, p);
        }

        if (p >= s.length() || p == -1) {
            param = "";
        } else {
            param = s.substring(p + 1, s.length());
        }
        cmd = cmd.toUpperCase();

        // cmdParam
        if (cmd.equals("USER")) {
            i = 1;
        }
        if (cmd.equals("PASS")) {
            i = 2;
        }
        if (cmd.equals("CDUP")) {
            i = 3;
        }
        if (cmd.equals("CWD")) {
            i = 4;
        }
        if (cmd.equals("QUIT"))
        {
            i = 5;
        }
        if (cmd.equals("RETR")) {
            i = 6;
        }
        if (cmd.equals("STOR")) {
            i = 7;
        }
        if (cmd.equals("PWD")) // répertoire courant
        {
            i = 8;
        }
        if (cmd.equals("LIST")) {
            i = 9;
        }
        if (cmd.equals("PORT")) {
            i = 10;
        }
        if (cmd.equals("TYPE")) {
            i = 11;
        }
        if (cmd.equals("NOOP")) {
            i = 12;
        }
        if (cmd.equals("PASV")) {
            i = 13;
        }
        return i;
    }// parseInput() end

    /**
     * valider l'existence de path donné dans param
     */
    int validatePath(String s) {
        File f = new File(s);
        if (f.exists() && !f.isDirectory()) {
            String s1 = s.toLowerCase();
            String s2 = rootdir.toLowerCase();
            if (s1.startsWith(s2)) {
                return 1;
            } else {
                return 0;
            }
        }
        f = new File(addTail(dir) + s);
        if (f.exists() && !f.isDirectory()) {
            String s1 = (addTail(dir) + s).toLowerCase();
            String s2 = rootdir.toLowerCase();
            if (s1.startsWith(s2)) {
                return 2;
            } else {
                return 0;
            }
        }
        return 0;
    }// validatePath() end

    /**
     * vérifier si le password est dans la liste de chaine qui a géré par
     * FtpServer.java
     * @param s
     * @return 
     */
    public boolean checkPASS(String s) {
        for (int i = 0; i < FtpServer.usersInfo.size(); i++) {
            if (((UserInfos) FtpServer.usersInfo.get(i)).user.equals(user)
                    && ((UserInfos) FtpServer.usersInfo.get(i)).password
                    .equals(s)) //
            {
                dir = ((UserInfos) FtpServer.usersInfo.get(i)).workDir;
                return true;
            }
        }
        return false;
    }// checkPASS() end

    /**
     * répondre à la commande USER pour un nom de compte
     * @return 
     */
    public boolean commandUSER() // User
    {
        if (cmd.equals("USER")) {
            reply = "331 User name OK, need password";
            user = param;
            state = FS_WAIT_PASS;
            return false;
        } else {
            reply = "501 Syntax error in parameters or arguments";
            return true;
        }

    }// commandUser() end

    /**
     * répondre à la commande PASS pour un password
     * @return 
     */
    public boolean commandPASS() {
        if (cmd.equals("PASS")) {
            if (checkPASS(param)) {
                reply = "230 User logged in, proceed";
                state = FS_LOGIN;
                System.out.println("Message: user " + user + " Form "
                        + remoteHost + "Login");
                System.out.print("-> ");
                return false;
            } else {
                reply = "530 Not logged in";
                return true;
            }
        } else {
            reply = "501 Syntax error in parameters or arguments";
            return true;
        }

    }// commandPass() end

    /**
     * donner erreur si commande unvalid
     */
    void errCMD() {
        reply = "500 Syntax error, command unrecognized";
    }

    /**
     * répondre à la commande CDUP égale CWD ..
     * @return 
     */
    public boolean commandCDUP() {
        dir = FtpServer.initDir;
        File f = new File(dir);
        if (f.getParent() != null && (!dir.equals(rootdir))) {
            dir = f.getParent();
            reply = "200 Command OK";
        } else {
            reply = "550 Current directory has no parent";
        }

        return false;
    }// commandCDUP() end

    /**
     * répondre à la commande CWD pour changer des répertoire
     * @return 
     */
    public boolean commandCWD() {
        File f = new File(param);
        String s = "";
        String s1 = "";
        if (dir.endsWith("/")) {
            s = dir;
        } else {
            s = dir + "/";
        }
        File f1 = new File(s + param);

        if (f.isDirectory() && f.exists()) {
            if (param.equals("..") || param.equals("..\\")) {
                if (dir.compareToIgnoreCase(rootdir) == 0) {
                    reply = "550 " + dir + ": No such file or directory.";
                    // return false;
                } else {
                    s1 = new File(dir).getParent();
                    if (s1 != null) {
                        dir = s1;
                        reply = "250 OK";
                    } else {
                        reply = "550 The directory does not exists";
                    }
                }
            } else if (param.equals(".") || param.equals(".\\")) {
            } else {
                dir = param;
                reply = "250 OK";
            }
        } else if (f1.isDirectory() && f1.exists()) {
            dir = s + param;
            reply = "250 OK";
        } else {
            reply = "501 Syntax error in parameters or arguments";
        }

        return false;
    } // commandCDW() end

    /**
     * écouter la commande QUIT pour sortir la connexion
     * @return 
     */
    public boolean commandQUIT() // QUITFtp
    {
        reply = "221 Service closing control connection";
        FtpServer.counter--;
        return true;
    }// commandQuit() end

    /**
     * écouter la commande PORT pour récuperer les info de client, ex: IP, PORT
     * @return 
     */
    public boolean commandPORT() {
        int p1 = 0;
        int p2 = 0;
        int[] a = new int[6];
        int i = 0;
        try {
            while ((p2 = param.indexOf(",", p1)) != -1) {
                a[i] = Integer.parseInt(param.substring(p1, p2));
                p2 = p2 + 1;
                p1 = p2;
                i++;
            }
            a[i] = Integer.parseInt(param.substring(p1, param.length()));
        } catch (NumberFormatException e) {
            reply = "501 Syntax error in parameters or arguments";
            return false;
        }

        remoteHost = a[0] + "." + a[1] + "." + a[2] + "." + a[3];
        remotePort = a[4] * 256 + a[5];
        mode = FTRANS_PORT;
        reply = "200 Command OK";
        return false;
    }// commandPort() end

    public boolean commandPASV() throws IOException {
        if (ssocket != null) {
            ssocket.close();
        }
        try {
            ssocket = new ServerSocket(0);
            int pPort = ssocket.getLocalPort();
            if (pPort < 1024) {
                pPort = 1025;
            }
            ssocket.setSoTimeout(100);
            reply = "227 entering passive mode (" + InetAddress.getLocalHost().getHostAddress().replace(',', '.') + "," + pPort + ")";
            if (ssocket != null) {
                dsocket = ssocket.accept();
            }
        } catch (Exception e) {
            if (ssocket != null) {
                ssocket.close();
                ssocket = null;
            }
        }
        mode = FTRANS_PASV;
        return false;
    }

    /**
     * répondre à la commande LIST et envoyer à client la list de répertoire
     * courant, devoir faire après PORT commande
     * @return 
     * @throws java.io.IOException
     */
    public boolean commandLIST() throws IOException {
        try {
            if (mode == FTRANS_PORT) {
                dsocket = new Socket(remoteHost, remotePort);
            } else if (mode == FTRANS_PASV) {
                dsocket = ssocket.accept();
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        try {
            PrintWriter dout = new PrintWriter(dsocket.getOutputStream(), true);
            out.println("125 Data connection already open; Transfer starting.");

            File f = new File(dir);   // current location
            String[] files = f.list();   // get current repertory file list
            String fileInfo;
            for (String file : files) {
                File f1 = new File(dir + "/" + file);
                PosixFileAttributes attrs = Files.getFileAttributeView(Paths.get(dir + "/" + file), PosixFileAttributeView.class).readAttributes();
                String permission = PosixFilePermissions.toString(attrs.permissions());
                String own = attrs.owner().getName();
                String group = attrs.group().getName();
                if(f1.isDirectory())
                    permission = "d" + permission;
                else
                    permission = "-" + permission;
                long size = attrs.size();
                // time mode
                long flm = f1.lastModified();
                Date fDate = new Date(flm);
                SimpleDateFormat time = new SimpleDateFormat("MMM dd hh:mm", Locale.ENGLISH);
                fileInfo = time.format(fDate) + " ";
                dout.print(permission +  " 1 " + own + " " + group + " ");
                dout.format("%10s",size);
                dout.println(" " + fileInfo + file);
            }
            dout.close();
            dsocket.close();
            reply = "226 Transfer complete !";
            return false;
        } catch (IOException e) {
            System.out.println("User don't receive imformation");
            reply = "451 Requested action aborted: local error in processing";
            return false;
        }
    }// commandLIST() end

    /**
     * répondre à la commande RETR pour télécharger un fichier indiqué, devoir
     * faire après PORT commande
     * @return 
     */
    public boolean commandRETR() {
        requestfile = param;
        File f = new File(requestfile);
        if (!f.exists()) {
            f = new File(addTail(dir) + param);
            if (!f.exists()) {
                reply = "550 File not found";
                return false;
            }
            requestfile = addTail(dir) + param;
        }

        if (isrest) {

        } else {
            if (type == FTYPE_IMAGE) {
                try {
                    out.println("150 Opening Binary mode data connection for "
                            + requestfile);
                    dsocket = new Socket(remoteHost, remotePort);
                    BufferedInputStream fin = new BufferedInputStream(
                            new FileInputStream(requestfile));
                    PrintStream dout = new PrintStream(
                            dsocket.getOutputStream(), true);
                    byte[] buf = new byte[1024];
                    int l = 0;
                    while ((l = fin.read(buf, 0, 1024)) != -1) {
                        dout.write(buf, 0, l);
                    }
                    fin.close();
                    dout.close();
                    dsocket.close();
                    reply = "226 Transfer complete !";

                } catch (Exception e) {
                    e.printStackTrace();
                    reply = "451 Requested action aborted: local error in processing";
                    return false;
                }

            }
            if (type == FTYPE_ASCII) {
                try {
                    out.println("150 Opening ASCII mode data connection for "
                            + requestfile);
                    dsocket = new Socket(remoteHost, remotePort);
                    BufferedReader fin = new BufferedReader(new FileReader(
                            requestfile));
                    PrintWriter dout = new PrintWriter(
                            dsocket.getOutputStream(), true);
                    String s;
                    while ((s = fin.readLine()) != null) {
                        dout.println(s);
                    }
                    fin.close();
                    dout.close();
                    dsocket.close();
                    reply = "226 Transfer complete !";
                } catch (Exception e) {
                    e.printStackTrace();
                    reply = "451 Requested action aborted: local error in processing";
                    return false;
                }
            }
        }
        return false;

    }// commandRETR() end

    /**
     * répondre à la commande STOR pour upload un fichier indiqué, devoir faire
     * après PORT commande
     * @return 
     */
    public boolean commandSTOR() {
        if (param.equals("")) {
            reply = "501 Syntax error in parameters or arguments";
            return false;
        }
        requestfile = addTail(dir) + param;
        if (type == FTYPE_IMAGE) {
            try {
                out.println("150 Opening Binary mode data connection for "
                        + requestfile);
                dsocket = new Socket(remoteHost, remotePort);
                BufferedOutputStream fout = new BufferedOutputStream(
                        new FileOutputStream(requestfile));
                BufferedInputStream din = new BufferedInputStream(
                        dsocket.getInputStream());
                byte[] buf = new byte[1024];
                int l = 0;
                while ((l = din.read(buf, 0, 1024)) != -1) {
                    fout.write(buf, 0, l);
                }// while()
                din.close();
                fout.close();
                dsocket.close();
                reply = "226 Transfer complete !";
            } catch (Exception e) {
                e.printStackTrace();
                reply = "451 Requested action aborted: local error in processing";
                return false;
            }
        }
        if (type == FTYPE_ASCII) {
            try {
                out.println("150 Opening ASCII mode data connection for "
                        + requestfile);
                dsocket = new Socket(remoteHost, remotePort);
                PrintWriter fout = new PrintWriter(new FileOutputStream(
                        requestfile));
                BufferedReader din = new BufferedReader(new InputStreamReader(
                        dsocket.getInputStream()));
                String line;
                while ((line = din.readLine()) != null) {
                    fout.println(line);
                }
                din.close();
                fout.close();
                dsocket.close();
                reply = " 226 Transfer complete !";
            } catch (Exception e) {
                e.printStackTrace();
                reply = "451 Requested action aborted: local error in processing";
                return false;
            }
        }
        return false;
    }// commandSTOR() end

    /**
     * écouter la commande PWD
     * @return 
     */
    public boolean commandPWD() // pwd
    {
        reply = "257 " + "\"" + dir + "\"";
        return false;
    }// commandPWD() end

    public boolean commandTYPE()
    {
        if (param.equals("A")) {
            type = FTYPE_ASCII;
            reply = "200 Command OK Change to ASCII mode";
        } else if (param.equals("I")) {
            type = FTYPE_IMAGE;
            reply = "200 Command OK Change to BINARY mode";
        } else {
            reply = "504 Command not implemented for that parameter";
        }

        return false;
    }//commandTYPE() end

    public boolean commandNOOP() {
        reply = "200 OK.";
        return false;
    }//commandNOOP() end

    String addTail(String s) {
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        return s;
    }

    public FtpRequest(Socket s, int i) {
        csocket = s;
        id = i;
    }

    /**
     * multithread fonction qui charge à analyser les commandes réçu par
     * FtpServer
     */
    public void run() //
    {
        String str = "";
        int parseResult;

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    csocket.getInputStream()));
            out = new PrintWriter(csocket.getOutputStream(), true);
            state = FS_WAIT_LOGIN;
            boolean finished = false;
            while (!finished) {
                str = in.readLine();
                if (str == null) {
                    finished = true;
                } else {
                    parseResult = parseInput(str);
                    System.out.println("Command:" + cmd + " Parameter:" + param);
                    System.out.print("-> ");
                    switch (state) {
                        case FS_WAIT_LOGIN:
                            finished = commandUSER();
                            break;
                        case FS_WAIT_PASS:
                            finished = commandPASS();
                            break;
                        case FS_LOGIN: {
                            switch (parseResult) {
                                case -1:
                                    errCMD();
                                    break;
                                case 3:
                                    finished = commandCDUP();
                                    break;
                                case 4:
                                    finished = commandCWD();
                                    break;
                                case 5:
                                    finished = commandQUIT();
                                    break;
                                case 6:
                                    finished = commandRETR();
                                    break;
                                case 7:
                                    finished = commandSTOR();
                                    break;
                                case 8:
                                    finished = commandPWD();
                                    break;
                                case 9:
                                    finished = commandLIST();
                                    break;
                                case 10:
                                    finished = commandPORT();
                                    break;
                                case 11:
                                    finished = commandTYPE();
                                    break;
                                case 12:
                                    finished = commandNOOP();
                                case 13:
                                    finished = commandPASV();
                            }// switch(parseResult) end
                        }// case FS_LOGIN: end
                        break;

                    }// switch(state) end
                } // else
                out.println(reply);
            } // while
            csocket.close();
        } // try
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}