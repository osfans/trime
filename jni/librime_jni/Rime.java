public class Rime
{
  static
  {
    System.loadLibrary("rime_jni");
  }

  public native long get_api(); 
  public native void set_notification_handler();

  // entry and exit
  public native void initialize();
  public native void finalize1();
  public native boolean start_maintenance(boolean full_check);
  public native boolean is_maintenance_mode();
  public native void join_maintenance_thread();

  // session management
  public native int create_session();
  public native boolean find_session(int session_id);
  public native boolean destroy_session(int session_id);
  public native void cleanup_stale_sessions();
  public native void cleanup_all_sessions();

  // input
  public native boolean process_key(int session_id, int key, int mask);
  public native boolean commit_composition(int session_id);
  public native void clear_composition(int session_id);

  // output
  public native boolean get_commit(int session_id);
  public native boolean get_status(int session_id);
  public native boolean get_context(int session_id);

  public native boolean simulate_key_sequence(int session_id, String s);
  public native String get_version();

  public void check() {
    boolean full_check = true;
    if (start_maintenance(full_check))join_maintenance_thread();
  }

  public static void main(String[] args){
    Rime test = new Rime(); 
    long rime = test.get_api();
    test.set_notification_handler();
    test.initialize();
    test.check();
    //int i =  test.create_session();
    //boolean r = test.simulate_key_sequence(i, "l");
    //r = test.simulate_key_sequence(i, "oa");
    //if(r)test.get_context(i);
    //System.out.println("r="+r);
    }
}
