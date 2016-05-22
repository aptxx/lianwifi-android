package aptxx.wifipassword;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.net.wifi.WifiManager;
import android.widget.TextView;


/*
    wifi密码查询器
    Github:https://github.com/aptxx/lianwifi-android.git
 */
public class MainActivity extends AppCompatActivity {
    // view
    public TextView txtView;
    // service
    public WifiManager wifiManager;

    public Scanner scanner;

    public Handler appendMessage = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            txtView.append(b.getString("message"));
        }
    };

    public Handler setMessage = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            txtView.setText(b.getString("message"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        txtView = (TextView) findViewById(R.id.textView);
        scanner = new Scanner(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // 选择栏选中事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_scan:
                scanner.run();
                break;
            case R.id.action_quit: // exit app
                finish();
                System.exit(0);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}