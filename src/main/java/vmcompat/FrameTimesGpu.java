package vmcompat;
import java.util.*;
/** Context-owned GPU-only half of FrameTimes. Its CPU swapped/history methods stay original. */
public final class FrameTimesGpu {
    interface Driver {boolean supported();void generate(int[] ids);void issue(int id);boolean ready(int id);long result(int id);void delete(int[] ids);}
    static final Driver NATIVE=new Driver(){public boolean supported(){return TimestampQueries.supported();}public void generate(int[] x){TimestampQueries.generate(x);}public void issue(int id){TimestampQueries.counter(id,org.lwjgl.opengl.GL33.GL_TIMESTAMP);}public boolean ready(int id){return TimestampQueries.available(id,org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE)!=0;}public long result(int id){return TimestampQueries.signedResult(id,org.lwjgl.opengl.GL15.GL_QUERY_RESULT);}public void delete(int[] x){TimestampQueries.delete(x);}};
    static final StateStore<Clock> CLOCKS=new StateStore<>(()->new Clock(NATIVE));
    static final class Clock {
        final Driver driver;final long[] frames=new long[6];int[] starts,ends;int open=-1;boolean failed,historyInitialized;
        Clock(Driver driver){this.driver=driver;Arrays.fill(frames,-1);}
        boolean begin(long count,float[] history){
            if(!historyInitialized){Arrays.fill(history,-1);historyInitialized=true;}
            if(failed||!driver.supported())return false;
            try{
                if(starts==null){starts=new int[6];ends=new int[6];driver.generate(starts);driver.generate(ends);}
                if(open>=0){frames[open]=-1;open=-1;} // unmatched start has no completed result
                for(int i=0;i<6;i++)if(frames[i]>=0&&frames[i]<count&&driver.ready(ends[i])){long frame=frames[i];long elapsed=driver.result(ends[i])-driver.result(starts[i]);frames[i]=-1;if(count-frame<4064&&elapsed>=0)history[(int)(frame&4095)]=elapsed/1_000_000f;}
                for(int i=0;i<6;i++)if(frames[i]<0){open=i;driver.issue(starts[i]);break;}return true;
            }catch(RuntimeException e){failed=true;open=-1;TimestampQueries.failure(e);return false;}
        }
        boolean end(long count){if(failed||!driver.supported())return false;try{if(open>=0){driver.issue(ends[open]);frames[open]=count;open=-1;}return true;}catch(RuntimeException e){failed=true;open=-1;TimestampQueries.failure(e);return false;}}
        void close(){if(starts!=null)driver.delete(starts);if(ends!=null)driver.delete(ends);starts=ends=null;open=-1;Arrays.fill(frames,-1);}
    }
    public static Boolean begin(long count,float[] history){try{return CLOCKS.get().begin(count,history);}catch(RuntimeException e){TimestampQueries.failure(e);return false;}}
    public static Boolean end(long count){try{return CLOCKS.get().end(count);}catch(RuntimeException e){TimestampQueries.failure(e);return false;}}
    static void closeCurrent(){CLOCKS.get().close();}
}
