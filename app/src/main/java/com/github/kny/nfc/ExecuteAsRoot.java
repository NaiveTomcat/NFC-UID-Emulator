package com.github.kny.nfc;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import android.util.Log;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.io.IOException;
import java.util.List;

public class ExecuteAsRoot
{
    public static boolean canRunRootCommands()
    {
        boolean retval = false;
        Process suProcess;
        try {
            suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
            BufferedReader osRes = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
            // Getting the id of the current user to check if this is root
            os.writeBytes("id\n");
            os.flush();
            String currUid = osRes.readLine();
            Log.d("Receive", currUid);
            if (currUid.contains("uid=0")) {
                retval = true;
                Log.d("ROOT", "Root access granted");
            } else {
                Log.d("ROOT", "Root access rejected: " + currUid);
            }
            os.writeBytes("exit\n");
            os.flush();
        } catch (Exception e) {
            // Can't get root !
            // Probably broken pipe exception on trying to write to output stream (os) after su failed, meaning that the device is not rooted
            retval = false;
            Log.d("ROOT", "Root access rejected [" + e.getClass().getName() + "] : " + e.getMessage());
        }

        return retval;
    }

    public static boolean execute(ArrayList<String> commands)
    {
        try {
            if (null != commands && !commands.isEmpty()) {
                Process suProcess = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
                // Execute commands that require root access
                for (String currCommand : commands) {
                    Log.d("ROOT", currCommand);
                    os.writeBytes(currCommand + "\n");
                    os.flush();
                    BufferedReader osRes = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));
                    if (osRes.ready()) {
                        Log.d("Receive", osRes.readLine());
                    }
                }
                os.writeBytes("exit\n");
                os.flush();
                try {
                    suProcess.waitFor();
                } catch (Exception ex) {
                    Log.e("ROOT", "Error executing root action", ex);
                    return false;
                }
            }
        } catch (IOException | SecurityException ex) {
            Log.w("ROOT", "Can't get root access", ex);
            return false;
        } catch (Exception ex) {
            Log.w("ROOT", "Error executing internal operation", ex);
            return false;
        }
        Log.d("ROOT", "Finish");
        return true;
    }

    protected static ArrayList<String> getCommandsToExecute(){
        return new ArrayList<>(List.of("cp /storage/emulated/0/RfidRecord/Copy.conf /etc/libnfc-nxp.conf"));
    }
}