import java.util.logging.Logger;

public class Test {
  private static Logger Log = Logger.getLogger(Test.class.getName());

  public static void main (String args[]) {
    Rime r = new Rime(); 
    Log.info("version=" + r.get_version());
    r.onText("test");
    Log.info(r.getCommitText());
    r.destroy();
  }
}

