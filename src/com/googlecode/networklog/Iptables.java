package com.googlecode.networklog;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.Log;
import android.content.Intent;

import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.BufferedWriter;


public class Iptables {
  public static final String SCRIPT = "networklog.sh";

  public static final String[] CELL_INTERFACES = {
    "rmnet+", "ppp+", "pdp+", "pnp+", "rmnet_sdio+", "uwbr+", "wimax+", "vsnet+", "usb+", "ccmni+"
  };

  public static final String[] WIFI_INTERFACES = {
    "eth+", "wlan+", "tiwlan+", "athwlan+", "ra+"
  };

  public static boolean checkRoot(Context context) {
    synchronized(NetworkLog.scriptLock) {
      String scriptFile = new ContextWrapper(context).getFilesDir().getAbsolutePath() + File.separator + SCRIPT;

      try {
        PrintWriter script = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));
        script.println("iptables");
        script.flush();
        script.close();
      } catch(java.io.IOException e) {
        Log.e("NetworkLog", "Check root error", e);
      }

      String error = new ShellCommand(new String[] { "su", "-c", "sh " + scriptFile }, "checkRoot").start(true);

      if(error != null) {
        Log.d("[NetworkLog]", "Failed check root: " + error);
        return false;
      } else {
        Log.d("[NetworkLog]", "Check root passed");
        return true;
      }
    }
  }

  public static boolean addRules(Context context) {
    if(checkRules(context) == true) {
      removeRules(context);
    }

    synchronized(NetworkLog.scriptLock) {
      String scriptFile = new ContextWrapper(context).getFilesDir().getAbsolutePath() + File.separator + SCRIPT;

      try {
        PrintWriter script = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));

        for(String iface : CELL_INTERFACES) {
          script.println("iptables -I OUTPUT 1 -o " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");

          script.println("iptables -I INPUT 1 -i " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");
        }

        for(String iface : WIFI_INTERFACES) {
          script.println("iptables -I OUTPUT 1 -o " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");

          script.println("iptables -I INPUT 1 -i " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");
        }

        script.flush();
        script.close();
      } catch(java.io.IOException e) {
        Log.e("NetworkLog", "addRules error", e);
      }

      String error = new ShellCommand(new String[] { "su", "-c", "sh " + scriptFile }, "addRules").start(true);

      if(error != null) {
        showError(context, "Add rules error", error);
        return false;
      }
    }

    return true;
  }

  public static boolean removeRules(Context context) {
    int tries = 0;

    while(checkRules(context) == true) {
      synchronized(NetworkLog.scriptLock) {
        String scriptFile = new ContextWrapper(context).getFilesDir().getAbsolutePath() + File.separator + SCRIPT;

        try {
          PrintWriter script = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));

          for(String iface : CELL_INTERFACES) {
            script.println("iptables -D OUTPUT -o " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");

            script.println("iptables -D INPUT -i " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");
          }

          for(String iface : WIFI_INTERFACES) {
            script.println("iptables -D OUTPUT -o " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");

            script.println("iptables -D INPUT -i " + iface + " -j LOG --log-prefix \"[NetworkLogEntry]\" --log-uid");
          }

          script.flush();
          script.close();
        } catch(java.io.IOException e) {
          Log.e("NetworkLog", "removeRules error", e);
        }

        String error = new ShellCommand(new String[] { "su", "-c", "sh " + scriptFile }, "removeRules").start(true);

        if(error != null) {
          showError(context, "Remove rules error", error);
          return false;
        }

        tries++;

        if(tries > 3) {
          MyLog.d("Too many attempts to remove rules, moving along...");
          return false;
        }
      }
    }

    return true;
  }

  public static boolean checkRules(Context context) {
    synchronized(NetworkLog.scriptLock) {
      String scriptFile = new ContextWrapper(context).getFilesDir().getAbsolutePath() + File.separator + SCRIPT;

      try {
        PrintWriter script = new PrintWriter(new BufferedWriter(new FileWriter(scriptFile)));
        script.println("iptables -L");
        script.flush();
        script.close();
      } catch(java.io.IOException e) {
        Log.e("NetworkLog", "checkRules error", e);
      }

      ShellCommand command = new ShellCommand(new String[] { "su", "-c", "sh " + scriptFile }, "checkRules");
      String error = command.start(false);

      if(error != null) {
        showError(context, "Check rules error", error);
        return false;
      }

      StringBuilder result = new StringBuilder();

      while(true) {
        String line = command.readStdoutBlocking();

        if(line == null) {
          break;
        }

        result.append(line);
      }

      if(result == null) {
        return true;
      }

      command.checkForExit();
      if(command.exit != 0) {
        showError(context, "Check rules error", result.toString());
        return false;
      }

      MyLog.d("checkRules result: [" + result + "]");

      return result.indexOf("[NetworkLogEntry]", 0) == -1 ? false : true;
    }
  }

  public static void showError(final Context context, final String title, final String message) {
    MyLog.d("Got error: [" + title + "] [" + message + "]");

    context.startActivity(new Intent(context, ErrorDialogActivity.class)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra("title", title)
        .putExtra("message", message));
  }
}