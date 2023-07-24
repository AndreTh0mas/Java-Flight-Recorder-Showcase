package ApplicationStatisticsCLI;
import jdk.jfr.consumer.*;
import org.apache.commons.lang3.tuple.Pair;
import java.nio.file.Path;
import java.util.*;
import static ApplicationStatisticsCLI.Formatters.*;
import static java.lang.Math.max;


public class ApplicationStatistics {
    private static float MACH_CPU = -1;
    private static float JVM_CPU = -1;
    private static float YC_COUNT = 0;
    private static float YC_MAX = -1;
    private static  double OC_TOT = 0;
    private static  double OC_AVG = 0;
    private static  float OC_COUNT = 0;
    private static  float OC_MAX = -1;
    private static float USED_HEAP = 0;
    private static float COM_HEAP = 0;
    private static float PHYSIC_MEM = -1;
    private static long THREADS = -1;
    private static long CLASSES = -1;
    private static long MAX_COM = -1;
    private static long INIT_HEAP = -1;
    private double TOT_ALLOC = 0;
    private long FirstAllocationTime = -1;
    private double ALLOC_RATE;

    private final HashMap<String,Float> TopThreadCPULoad = new HashMap<>();
    private final Comparator<Pair<String, Float>> customComparator = Comparator.comparing(Pair::getValue);
    private final PriorityQueue<Pair<String, Float>> TopCPULoadUniqueThreads = new PriorityQueue<>(customComparator);
    private final HashMap<String,Float> HotMethods = new HashMap<>();
    private final HashMap<String,Float> HotMethodsAllocation = new HashMap<>();

    double TotalCountForHotMethods = 0;
    double TotalCountForAllocations = 0;

    private final HashMap<String,Float> ThreadSampleCount = new HashMap<>();
    private final HashMap<String,HashMap<String,Float>> ThreadHotMethod = new HashMap<>();
    private static final HashMap<String,Float> Top5HotMethods = new HashMap<>();

    private static final String TEMPLATE =
            """
            ============================ APPLICATION STATS ===============================
            | Phys. memory: $PHYSIC_MEM                         Alloc Rate: $ALLOC_RATE  |
            | OC Count    : $OC_COUNT Initial Heap: $INIT_HEAP  Total Alloc: $TOT_ALLOC  |
            | OC Pause Avg: $OC_AVG   Used Heap   : $USED_HEAP  Thread Count: $THREADS   |
            | OC Pause Max: $OC_MAX   Commit. Heap: $COM_HEAP   Class Count : $CLASSES   |
            | YC Count    : $YC_COUNT CPU Machine : $MACH_CPU   Max Comp. Time: $MAX_COM |
            | YC Pause Max: $YC_MAX   CPU JVM     :$JVM_CPU                              |
            |------------------------ Top Allocation Methods ----------------------------|
            | $ALLOCATION_TOP_FRAME                                             $AL_PE   |
            | $ALLOCATION_TOP_FRAME                                             $AL_PE   |
            | $ALLOCATION_TOP_FRAME                                             $AL_PE   |
            | $ALLOCATION_TOP_FRAME                                             $AL_PE   |
            | $ALLOCATION_TOP_FRAME                                             $AL_PE   |
            |----------------------------- Hot Methods ----------------------------------|
            | $EXECUTION_TOP_FRAME                                              $EX_PE   |
            | $EXECUTION_TOP_FRAME                                              $EX_PE   |
            | $EXECUTION_TOP_FRAME                                              $EX_PE   |
            | $EXECUTION_TOP_FRAME                                              $EX_PE   |
            | $EXECUTION_TOP_FRAME                                              $EX_PE   |
            |-------------------------- Top CPULoad Threads -----------------------------|
            | $HIGH_CPU_THREAD                                                  $TX_PE   |
            | $HIGH_CPU_THREAD                                                  $TX_PE   |
            | $HIGH_CPU_THREAD                                                  $TX_PE   |
            | $HIGH_CPU_THREAD                                                  $TX_PE   |
            | $HIGH_CPU_THREAD                                                  $TX_PE   |
            |                                                                            |
            |------ Hot Methods on High CPULoad Threads Having StackTrace Available------|
            """;

    private static final String ThreadCPULoadTEMPLATE = """
            | $THREAD_NAME                                                      $HX_PE   |
            |        --------------------------------------------------------            |
            | $HOT_METHOD_CPU_THREAD                                            $BX_PE   |
            | $HOT_METHOD_CPU_THREAD                                            $BX_PE   |
            | $HOT_METHOD_CPU_THREAD                                            $BX_PE   |
            | $HOT_METHOD_CPU_THREAD                                            $BX_PE   |
            | $HOT_METHOD_CPU_THREAD                                            $BX_PE   |
            ==============================================================================
            """;

    private void onCPULoad(RecordedEvent event) {
        MACH_CPU = max(MACH_CPU,(float)event.getValue("machineTotal"));
        JVM_CPU = max(JVM_CPU,(float)event.getValue("jvmSystem") + (float)event.getValue("jvmUser"));
    }
    private void onYoungGarbageCollection(RecordedEvent event){
        YC_COUNT++;
        YC_MAX = max(YC_MAX,event.getDuration().toNanos());
    }
    private void onOldCollection(RecordedEvent event){
        OC_COUNT++;
        OC_MAX = max(OC_MAX,event.getDuration().toNanos());
        OC_TOT+=event.getDuration().toNanos();
    }
    private void onGCSummary(RecordedEvent event){
        USED_HEAP = max(USED_HEAP,(long)event.getValue("heapUsed"));
        COM_HEAP = max(COM_HEAP,(long)event.getValue("heapSpace.committedSize")); // As committed size can change dynamically during the execution of the program.
    }
    private void onPhysicalMemory(RecordedEvent event){
        PHYSIC_MEM = event.getLong("totalSize");
    }
    private void onThreadStats(RecordedEvent event){
        THREADS = max(THREADS,event.getLong("peakCount")); // Represents maximum number of active Threads till event time
    }
    private void onClassLoadingStatistics(RecordedEvent event) { // Represents maximum number Loaded class at the moment
        long diff = event.getLong("loadedClassCount") - event.getLong("unloadedClassCount");
        CLASSES = max(CLASSES,diff);
    }
    private void onCompilation(RecordedEvent event){
        MAX_COM = max(MAX_COM,event.getDuration().toNanos());
    }
    private void onGCHeapConfiguration(RecordedEvent event){
        INIT_HEAP = event.getLong("initialSize");
    }
    private void printReport() {
        try {
            StringBuilder template = new StringBuilder(TEMPLATE);
            String variable = "$PHYSIC_MEM";
            String value = formatBytes(PHYSIC_MEM);
            writeParam(template,variable,value);
            variable = "$TOT_ALLOC";
            value = formatBytes(TOT_ALLOC);
            writeParam(template,variable,value);
            variable = "$MAX_COM";
            value = formatDuration(MAX_COM);
            writeParam(template,variable,value);
            variable = "$CLASSES";
            Long temp = (Long) CLASSES;
            writeParam(template,variable,temp.toString());
            variable = "$THREADS";
            temp = (Long) THREADS;
            writeParam(template,variable,temp.toString());
            variable = "$ALLOC_RATE";
            value = formatBytes((Number) ALLOC_RATE) + "/s";
            writeParam(template,variable,value);
            variable = "$INIT_HEAP";
            value = formatBytes(INIT_HEAP);
            writeParam(template,variable,value);
            variable = "$USED_HEAP";
            value = formatBytes(USED_HEAP);
            writeParam(template,variable,value);
            variable = "$COM_HEAP";
            value = formatBytes(COM_HEAP);
            writeParam(template,variable,value);
            variable = "$MACH_CPU";
            value = formatPercentage(MACH_CPU);
            writeParam(template,variable,value);
            variable = "$JVM_CPU";
            value = formatPercentage(JVM_CPU);
            writeParam(template,variable,value);
            variable = "$OC_COUNT";
            temp = (long) OC_COUNT;
            writeParam(template,variable,temp.toString());
            variable = "$YC_COUNT";
            temp = (long) YC_COUNT;
            writeParam(template,variable,temp.toString());
            variable = "$OC_MAX";
            value = formatDuration(OC_MAX);
            writeParam(template,variable,value);
            variable = "$YC_MAX";
            value = formatDuration(YC_MAX);
            writeParam(template,variable,value);
            OC_AVG =(long) OC_TOT/OC_COUNT;
            variable = "$OC_AVG";
            value = formatDuration(OC_AVG);
            writeParam(template,variable,value);
            // Inserting Allocations.
            // Sort the HashMap by value in descending order
            List<Map.Entry<String, Float>> sortedEntriesAllocation = HotMethodsAllocation.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                    .toList();
            List<Map.Entry<String, Float>> sortedEntriesMethods = HotMethods.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                    .toList();
            List<Map.Entry<String, Float>> sortedEntriesThreadCPULoad = TopThreadCPULoad.entrySet()
                    .stream()
                    .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                    .toList();
            for(int i = 0;i<5;i++){
                variable = "$ALLOCATION_TOP_FRAME";
                value = "N/A";
                if(i<sortedEntriesAllocation.size()) {
                    Map.Entry<String, Float> entry = sortedEntriesAllocation.get(i);
                    value = entry.getKey();
                }
                writeParam(template,variable,value);
                variable = "$AL_PE";
                value = "N/A";
                if(i<sortedEntriesAllocation.size()) {
                    Map.Entry<String, Float> entry = sortedEntriesAllocation.get(i);
                    value = formatPercentage(entry.getValue()/TotalCountForAllocations);
                }
                writeParam(template,variable,value);
            }
            // Inserting the HotMethods
            for(int i = 0;i<5;i++){
                variable = "$EXECUTION_TOP_FRAME";
                value = "N/A";
                if(i<sortedEntriesMethods.size()) {
                    Map.Entry<String, Float> entry = sortedEntriesMethods.get(i);
                    value = entry.getKey();
                    Top5HotMethods.put(entry.getKey(),entry.getValue());
                }
                writeParam(template,variable,value);
                variable = "$EX_PE";
                value = "N/A";
                if(i<sortedEntriesMethods.size()) {
                    Map.Entry<String, Float> entry = sortedEntriesMethods.get(i);
                    value = formatPercentage(entry.getValue()/TotalCountForHotMethods);
                }
                writeParam(template,variable,value);
            }
            // Inserting the Top Threads
            for(int i = 0;i<5;i++){
                variable = "$HIGH_CPU_THREAD";
                value = "N/A";
                if(i<sortedEntriesThreadCPULoad.size()) {
                    Map.Entry<String, Float> entry = sortedEntriesThreadCPULoad.get(i);
                    value = entry.getKey();
                }
                writeParam(template,variable,value);
                variable = "$TX_PE";
                value = "N/A";
                if(i<sortedEntriesThreadCPULoad.size()) {
                    Map.Entry<String, Float> entry = sortedEntriesThreadCPULoad.get(i);
                    value = formatPercentage(entry.getValue());
                }
                writeParam(template,variable,value);
            }


            System.out.println(template.toString());

            // Inserting HotMethods of the High CPU Threads
            int PrintCount = 0;
            for(Map.Entry<String,Float> entry: sortedEntriesThreadCPULoad){
                if(ThreadSampleCount.containsKey(entry.getKey())){ //  entry.getKey -> gets the ThreadName
                    StringBuilder ThreadTemplate = new StringBuilder(ThreadCPULoadTEMPLATE);
                    variable = "$THREAD_NAME";
                    value = entry.getKey();
                    writeParam(ThreadTemplate,variable,value);
                    variable = "$HX_PE";
                    value = formatPercentage(entry.getValue());
                    writeParam(ThreadTemplate,variable,value);
                    List<Map.Entry<String, Float>> ThreadHotMethodEntry = ThreadHotMethod.get(entry.getKey()).entrySet()
                            .stream()
                            .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                            .toList();
                    for(int i = 0;i<5;i++){
                        variable = "$HOT_METHOD_CPU_THREAD";
                        value = "N/A";
                        if(i<ThreadHotMethodEntry.size()) {
                            Map.Entry<String, Float> Entry = ThreadHotMethodEntry.get(i);
                            value = Entry.getKey();
                        }
                        writeParam(ThreadTemplate,variable,value);
                        variable = "$BX_PE";
                        value = "N/A";
                        if(i<ThreadHotMethodEntry.size()) {
                            Map.Entry<String, Float> Entry = ThreadHotMethodEntry.get(i);
                            value = formatPercentage(Entry.getValue()/ThreadSampleCount.get(entry.getKey()));
                        }
                        writeParam(ThreadTemplate,variable,value);
                    }
                    PrintCount++;
                    System.out.println(ThreadTemplate.toString());
                    if(PrintCount == 5){
                        break;
                    }
                }
            }

        }
        catch(Exception ex){
            ex.printStackTrace();
        }
    }

/*

    To process only specific events, we could read events one by one with RecordingFile.readEvent(),
    as above, then check the event's name. However, if we use the event streaming API,
    then event objects of the same type are reused to reduced allocation pressure, but it is only available including and after jdk-14.

*/
    public void Runner(Path file) throws Exception{
            try (RecordingFile recordingFile = new RecordingFile(file)) { // Reads the events from the file. From already recorded file
                while (recordingFile.hasMoreEvents()) {
                    RecordedEvent e = recordingFile.readEvent(); // Reads the next event if exists
                    String EventName = e.getEventType().getName();
                    if(EventName.equals("jdk.ThreadCPULoad")){
                        String eventThread = e.getValue("eventThread.osName").toString();
                        float CPULoad = e.getFloat("user")+ e.getFloat("system");
                        if(TopCPULoadUniqueThreads.size()<20){
                            TopCPULoadUniqueThreads.add(Pair.of(eventThread,CPULoad));
                        }
                        Pair<String,Float> TopElement = TopCPULoadUniqueThreads.peek();
                        assert TopElement != null;

                        if(TopElement.getRight() < CPULoad){ // Now we are going to add the Pair to our Priority_queue
                            // First case, we are just updating the value.
                            if(TopThreadCPULoad.containsKey(eventThread)){ // Checking if the eventThread is already in the Map
                                // Means it is also available in the priority queue;
                                if(TopThreadCPULoad.get(eventThread) < CPULoad) {
                                    TopCPULoadUniqueThreads.remove(Pair.of(eventThread, TopThreadCPULoad.get(eventThread)));
                                    TopCPULoadUniqueThreads.add(Pair.of(eventThread, CPULoad));
                                    TopThreadCPULoad.put(eventThread, CPULoad);
                                }
                            }
                            else{
                                if(TopCPULoadUniqueThreads.size()>20){
                                    TopCPULoadUniqueThreads.poll();
                                    TopThreadCPULoad.remove(TopElement.getLeft());
                                }
                                TopCPULoadUniqueThreads.add(Pair.of(eventThread,CPULoad));
                                TopThreadCPULoad.put(eventThread,CPULoad);
                            }

                        }
                    }
                    if(EventName.equals("jdk.ExecutionSample")){
                        try {
                            List<RecordedFrame> frames = e.getStackTrace().getFrames(); // getting all the frames from the Execution event.
                            if (!frames.isEmpty()) {
                                RecordedFrame topFrame = frames.get(0);
                                if (topFrame.isJavaFrame()) {
                                    String ThreadName = e.getValue("sampledThread.javaName").toString();
                                    if(ThreadSampleCount.containsKey(ThreadName)){
                                        ThreadSampleCount.put(ThreadName,ThreadSampleCount.get(ThreadName)+1);
                                    }
                                    else{
                                        ThreadSampleCount.put(ThreadName,(float)1);
                                    }
                                    TotalCountForHotMethods++;
                                    String topMethod = formatMethod(topFrame.getMethod());
                                    HotMethods.merge(topMethod, (float) 1,Float::sum);
                                    if(ThreadHotMethod.containsKey(ThreadName)){
                                        ThreadHotMethod.get(ThreadName).merge(topMethod,(float)1,Float::sum);
                                    }
                                    else{
                                        ThreadHotMethod.put(ThreadName,new HashMap<>());
                                        ThreadHotMethod.get(ThreadName).put(topMethod,(float)1);
                                    }

                                }
                            }
                        } catch(Exception exception){
                            exception.printStackTrace();
                        }

                    }
                    if(EventName.equals("jdk.ObjectAllocationSample")){
                        try {
                            List<RecordedFrame> frames = e.getStackTrace().getFrames(); // getting all the frames from the Execution event.
                            if (!frames.isEmpty()) {
                                RecordedFrame topFrame = frames.get(0);
                                if (topFrame.isJavaFrame()) {
                                    TotalCountForAllocations++;
                                    String topMethodAllocation = formatMethod(topFrame.getMethod());
                                    if (HotMethodsAllocation.containsKey(topMethodAllocation)) {
                                        HotMethodsAllocation.put(topMethodAllocation, HotMethodsAllocation.get(topMethodAllocation) + 1);
                                    } else {
                                        HotMethodsAllocation.put(topMethodAllocation, (float) 1);
                                    }
                                }
                            }
                            long size = e.getLong("weight");
                            TOT_ALLOC += size;
                            long timestamp = e.getEndTime().toEpochMilli();
                            if (FirstAllocationTime > 0) {
                                long elapsedTime = timestamp - FirstAllocationTime;
                                if (elapsedTime > 0) {
                                    ALLOC_RATE = 1000.0 * (TOT_ALLOC / elapsedTime);
                                }
                            } else {
                                FirstAllocationTime = timestamp;
                            }
                        }catch(Exception exception){
                            exception.printStackTrace();
                        }
                    }
                    if(EventName.equals("jdk.CPULoad")){
                        onCPULoad(e);
                    }
                    if(EventName.equals("jdk.YoungGarbageCollection")){
                        onYoungGarbageCollection(e);
                    }
                    if(EventName.equals("jdk.OldGarbageCollection")){
                        onOldCollection(e);
                    }
                    if(EventName.equals("jdk.GCHeapSummary")){
                        onGCSummary(e);
                    }
                    if(EventName.equals("jdk.PhysicalMemory")){
                        onPhysicalMemory(e);
                    }
                    if(EventName.equals("jdk.JavaThreadStatistics")){
                        onThreadStats(e);
                    }
                    if(EventName.equals("jdk.ClassLoadingStatistics")){
                        onClassLoadingStatistics(e);
                    }
                    if(EventName.equals("jdk.Compilation")){
                        onCompilation(e);
                    }
                    if(EventName.equals("jdk.GCHeapConfiguration")){
                        onGCHeapConfiguration(e);
                    }
                }
                printReport();
            }
        }
        public static void main(String[] args) throws Exception {
            ApplicationStatistics TimePass = new ApplicationStatistics();
            Path file = Path.of("/Users/harsh.kumar/Downloads/flight_recording-2.jfr");
            TimePass.Runner(file);
            System.out.println("|================ Sprinklr Methods Contribution to Hot Methods ==============|\n");
            SprinklrMethodStats SprinklrPrint = new SprinklrMethodStats(Top5HotMethods);
            SprinklrPrint.Main(file);
        }
    }

