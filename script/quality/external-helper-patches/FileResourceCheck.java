import com.reecedunn.espeak.FileUtils;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class FileResourceCheck {
 static long handles(Path path) throws Exception {
  long count=0;
  try (DirectoryStream<Path> entries=Files.newDirectoryStream(Paths.get("/proc/self/fd"))) {
   for(Path entry:entries) { try { if(Files.readSymbolicLink(entry).equals(path)) count++; } catch(NoSuchFileException ignored) {} }
  }
  return count;
 }
 static class ThrowingLength extends File {
  private static final long serialVersionUID=1L;
  ThrowingLength(String path){super(path);}
  @Override public long length(){throw new IllegalStateException("synthetic length failure");}
 }
 static class Borrowed extends ByteArrayInputStream {
  boolean closed;
  Borrowed(byte[] b){super(b);}
  @Override public void close()throws IOException {closed=true;super.close();}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception {
  boolean patched=Boolean.parseBoolean(args[0]);
  Path file=Paths.get(args[1]).toAbsolutePath();byte[] data="synthetic_resource_2026".getBytes("UTF-8");Files.write(file,data);
  check(handles(file)==0,"pre-existing handles");
  for(int i=0;i<64;i++){check(FileUtils.read(file.toFile()).equals("synthetic_resource_2026"),"text");check(Arrays.equals(FileUtils.readBinary(file.toFile()),data),"binary");}
  long afterReads=handles(file);
  for(int i=0;i<8;i++)for(boolean binary:new boolean[]{false,true}){
   try {if(binary)FileUtils.readBinary(new ThrowingLength(file.toString()));else FileUtils.read(new ThrowingLength(file.toString()));throw new AssertionError("exception missing");}
   catch(IllegalStateException expected){check(expected.getMessage().equals("synthetic length failure"),"exception changed");}
  }
  long afterFailure=handles(file);
  Borrowed text=new Borrowed(data);Borrowed binary=new Borrowed(data);
  check(FileUtils.read(text).equals("synthetic_resource_2026")&&!text.closed,"borrowed text ownership");
  check(Arrays.equals(FileUtils.readBinary(binary),data)&&!binary.closed,"borrowed binary ownership");text.close();binary.close();
  check(patched ? afterReads==0&&afterFailure==0 : afterReads==128&&afterFailure==144,"FD contract: "+afterReads+","+afterFailure);
  System.out.println("{\"patched\":"+patched+",\"read_calls\":128,\"failure_calls\":16,\"fd_after_reads\":"+afterReads+",\"fd_after_failures\":"+afterFailure+",\"borrowed_ownership_preserved\":true}");
 }
}
