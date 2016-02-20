package aptxx.wifipassword;

import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by prestor on 16/02/19.
 */
public class Scanner {
    // vars
    private String retSn;
    private String dhid;
    private String ii;
    private String mac;
    // state
    private int scannerState;
    private int retSnState;
    private int dhidState; // 0: ok 1: need update 2: updating

    //
    private MainActivity mainActivity;

    public  Scanner(MainActivity ma) {
        mainActivity = ma;
        retSn = "e10adc3949ba59abbe56e057f20f883e"; // md5('123456')
        dhid = null;
        ii = null;
        mac = null;
        scannerState = 0;
        retSnState = 1;
        dhidState = 1;
    }

    public void run() {
        if (scannerState == 0)
            new Thread(scanRunnable).start();
    }

    public Runnable scanRunnable = new Runnable() {
        @Override
        public synchronized void run() {
            scannerState = 1; // running
            sendMessageToUI("", 'w'); // clear textView
            sendMessageToUI("Scanning..\n\n", 'a');

            checkWifiOn();
            List<ScanResult> wifis = mainActivity.wifiManager.getScanResults();
            if ( wifis.isEmpty() ) {
                sendMessageToUI("（/TДT)/ 搜索不到附近的wifi,你的手机在荒野迷失了..\n", 'w');
                scannerState = 0;
                return ;
            }

            String bssids = "";
            String ssids = "";
            HashMap wifiLevel = new HashMap(); // 信号强度
            if (dhidState == 1) registerNewDevice();
            if (retSnState == 1) getNewRetSn();
            for (ScanResult wifi: wifis) {
                bssids = bssids + "," + wifi.BSSID;
                ssids = ssids + "," + wifi.SSID;
                wifiLevel.put(wifi.BSSID, wifi.level);
            }

            // request
            String result = queryPassword(bssids, ssids, dhid, ii, mac, retSn);
            try {
                JSONObject data = new JSONObject(result);
                retSn = data.getString("retSn");
                if(data.getInt("retCd") != 0) {
                    throw new Exception(data.getString("retMsg"));
                }
                if(data.getJSONObject("qryapwd").getInt("retCd") != 0) {
                    throw new Exception(data.getJSONObject("qryapwd").getString("retMsg"));
                }

                JSONObject psws = data.getJSONObject("qryapwd").getJSONObject("psws");

                // query success but no password found
                if (psws.length() <= 0) {
                    sendMessageToUI("没有可用wifi,真是遗憾..\n" ,'a');
                    return;
                }
                // found
                Iterator<String> keys = psws.keys();
                while(keys.hasNext()) {
                    JSONObject wf = psws.getJSONObject(keys.next());
                    StringBuilder sb = new StringBuilder(); // string that sent to UI
                    int level = Integer.parseInt( wifiLevel.get(wf.getString("bssid")).toString() );
                    sb.append("SSID: " + wf.getString("ssid") + " (" + wf.getString("bssid") +") - Level:"+ level + "\n");
                    sb.append("Password: " + decryptPassword(wf.getString("pwd")) + "\n");

                    // xUser & xPwd
                    if (! wf.getString("xUser").isEmpty()) {
                        sb.append("xUser: " + wf.getString("xUser") + "\n");
                        sb.append("xPwd:" + wf.getString("xPwd") + "\n");
                    }

                    // update UI
                    sb.append("\n");
                    sendMessageToUI(sb.toString(), 'a');
                }
            } catch (Exception e) { // exception
                dhidState = 1;
                retSnState = 1;
                sendMessageToUI(e.getMessage() + "\nSorry! Try again please..\n", 'a');
            } finally {
                scannerState = 0; // scanner finished
            }
        }
    };

    public void checkWifiOn() {
        if (! mainActivity.wifiManager.isWifiEnabled()) {
            this.sendMessageToUI("wifi is disabled..making it enabled\n\n", 'a');
            mainActivity.wifiManager.setWifiEnabled(true);
        }
    }

    public void sendMessageToUI(String str, Character flag) {
        Bundle b = new Bundle();
        b.putString("message", str);
        Message msg = new Message();
        msg.setData(b);
        switch (flag) {
            case 'a': mainActivity.appendMessage.sendMessage(msg);break;
            case 'w': mainActivity.setMessage.sendMessage(msg);break;
            default: break;
        }

    }

    // 注册新设备
    public void registerNewDevice() {
        sendMessageToUI("获取新的dhid..", 'a');
        String salt = "1Hf%5Yh&7Og$1Wh!6Vr&7Rs!3Nj#1Aa$";
        Random random = new Random();
        ii = md5(Integer.toString(random.nextInt()));
        mac = ii.substring(0, 12);
        dhid = null;

        HashMap hm = new HashMap();
        hm.put("appid", "0008");
        hm.put("chanid", "gw");
        hm.put("os", "Android");
        hm.put("osvercd", "4.4.0");
        hm.put("wkver", "324");
        hm.put("ii", ii);
        hm.put("lang", "cn");
        hm.put("mac", mac);
        hm.put("method", "getTouristSwitch");
        hm.put("pid", "initdev:commonswitch");
        hm.put("st", "m");
        hm.put("uhid", "a0000000000000000000000000000001");
        hm.put("v", "324");
        hm.put("sign", sign(hm, salt));

        try {
            JSONObject json = new JSONObject(request(hm));
            dhid = json.getJSONObject("initdev").getString("dhid");
            dhidState = 0;
            sendMessageToUI(" -- ok\n\n", 'a');
        } catch (Exception e) {
            sendMessageToUI(" -- fail\nTry again..after 3s\n\n", 'a');
            SystemClock.sleep(3000);
            registerNewDevice();
        }
    }

    // 新的retSn
    public void getNewRetSn() {
        sendMessageToUI("获取新的retSn..", 'a');
        String response = queryPassword("aa:aa:aa:aa:aa:aa", "aptxx", dhid, ii, mac, retSn);
        try {
            JSONObject data = new JSONObject(response);
            retSn = data.getString("retSn");
            retSnState = 0;
            sendMessageToUI(" -- ok\n\n", 'a');
        } catch (Exception e) {
            sendMessageToUI(" -- fail\nTry again..after 3s\n\n", 'a');
            SystemClock.sleep(3000);
            getNewRetSn();
        }
    }

    // 查询
    public String queryPassword(String bssid, String ssid, String dhid, String ii, String mac, String retSn) {
        HashMap hm = new HashMap();
        hm.put("appid", "0008");
        hm.put("bssid", bssid);
        hm.put("chanid", "gw");
        hm.put("dhid", dhid);
        hm.put("ii", ii);
        hm.put("lang", "cn");
        hm.put("mac", mac);
        hm.put("method", "getDeepSecChkSwitch");
        hm.put("pid", "qryapwd:commonswitch");
        hm.put("ssid", ssid);
        hm.put("st", "m");
        hm.put("uhid", "a0000000000000000000000000000001");
        hm.put("v", "324");
        hm.put("sign", this.sign(hm, retSn));

        return request(hm);
    }

    // http请求
    public String request(HashMap<String, String> data) {
        StringBuilder urlParametersBuilder = new StringBuilder();
        Iterator iter = data.keySet().iterator();
        String key = null;
        if(! iter.hasNext()) return "";
        while (true) {
            key = iter.next().toString();
            urlParametersBuilder.append(key + "=" + data.get(key).toString());
            if(iter.hasNext()){
                urlParametersBuilder.append("&");
                continue;
            } else {
                break;
            }
        }
        String urlParameters = urlParametersBuilder.toString();
        try {
            URL url = new URL("http://wifiapi02.51y5.net/wifiapi/fa.cmd");
            String userAgent = "WiFiMasterKey/1.1.0 (Mac OS X Version 10.10.3 (Build 14D136))";

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("User-Agent", userAgent);
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.getOutputStream();
            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            out.write(urlParameters.getBytes());
            out.close();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(urlConnection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            urlConnection.disconnect();
            return response.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }

    // 生成指纹
    public String sign(Map data, String salt) {
        Object[] arrayOfObject = data.keySet().toArray();
        Arrays.sort(arrayOfObject);
        StringBuilder localStringBuilder = new StringBuilder();
        int i = arrayOfObject.length;
        for(int j=0; j<i; j++) {
            localStringBuilder.append((String)data.get(arrayOfObject[j]));
        }
        localStringBuilder.append(salt);
        return this.md5(localStringBuilder.toString()).toUpperCase();
    }

    // 解密wifi密码(AES/CBC/NoPadding)
    public  String decryptPassword(String str) {
        String key = "k%7Ve#8Ie!5Fb&8E";
        String iv = "y!0Oe#2Wj#6Pw!3V";
        byte[] b = null;
        String result = null;
        try {
            Cipher cipher =  Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keyspec = new SecretKeySpec(key.getBytes(), "AES");
            IvParameterSpec ivspec = new IvParameterSpec(iv.getBytes());
            {
                cipher.init(2, keyspec, ivspec);
                if (str != null && str.length() > 2) {
                    int i = str.length() / 2;
                    b = new byte[i];
                    for (int j = 0; j < i; j++) {
                        String tmp_string = str.substring(j * 2, j * 2 + 2);
                        b[j] = ((byte) Integer.parseInt(tmp_string, 0x10));
                    }
                }
                result = new String(cipher.doFinal(b));
            }
        } catch (Exception e) {
            return "Decrypt Failed!";
        }
        result = result.trim();
        int pwdLength = Integer.parseInt(result.substring(0, 3));
        return result.substring(3, 3 + pwdLength);
    }

    // 字符串md5摘要
    public String md5(String str) {
        if (str == null || str == "") {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(str.getBytes());
            byte b[] = md.digest();
            int i;
            StringBuffer buf = new StringBuffer("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if (i < 0)
                    i += 256;
                if (i < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }
            // 32 B
            return buf.toString();
            // 16 B
            //return buf.toString().substring(8, 24);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
