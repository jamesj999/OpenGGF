import com.openggf.tests.trace.TraceData;
import java.nio.file.Path;
public class CheckLag { public static void main(String[] a) throws Exception { var t=TraceData.load(Path.of("src/test/resources/traces/s1/mz1_fullrun")); for(int f=754;f<=761;f++){ System.out.println(f+" lag="+t.isLagFrame(f)+" rings="+t.getFrame(f).rings()); } } }
