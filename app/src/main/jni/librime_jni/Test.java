import java.util.logging.Logger;

public class Test {
  private static Logger Log = Logger.getLogger(Test.class.getName());

  public static void main (String args[]) {
    Rime r = Rime.get(false);
    Log.info("version1=" + r.get_version());
    r.onText("adb");
    //Log.info(r.getCommitText());
    r.destroy();
  }
}

