import clonedetect.CloneDetector;
import clonedetect.Data;
import clonedetect.PreProcess;
import myutils.Func;
import myutils.SolidityExtract;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;


public class Main {
    private static String projectPath  = "";
    //source files
    // private static String projectPath = null;
    private static int threadNum = 50;
    private static int partitionNum = 1;
    private static int N = 10;
    private static String outputPath = "./output/file/";

    public static void main(String[] args) {
        setParameter(args);
        if(N > Data.minLine_num) {
            System.out.println("N can't be greater than min line number!!!");
            System.exit(0);
        }
        long startTime = System.currentTimeMillis();

        SolidityExtract javaFileExtract = new SolidityExtract();
        ArrayList<String> allJavaFiles= javaFileExtract.GetDirectory(projectPath);

        int files_num = allJavaFiles.size(); 
        
        System.out.println("All files num:"+files_num);

        Data data = new Data(N, projectPath, allJavaFiles);

        int divFileNum = (files_num + threadNum - 1)/threadNum;
        PreProcess[] processes = new PreProcess[threadNum];
        final CountDownLatch latch1 = new CountDownLatch(threadNum);
        for (int i = 0; i < threadNum-1; i++) {
            processes[i] = new PreProcess(i*divFileNum, Math.min((i+1)*divFileNum, files_num), data, latch1);
            processes[i].t.start();
        }
        processes[threadNum-1] = new PreProcess((threadNum-1)*divFileNum, files_num, data, latch1);
        processes[threadNum-1].t.start();
        try {
            latch1.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("All functions num:"+data.allFuncs.size());

        Main.printMemoryCondition();
        allJavaFiles.clear();
        allJavaFiles = null;
        processes = null;

        long endTime   = System.currentTimeMillis();
        long TotalTime = endTime - startTime;
        System.out.println("Preprocess time: "+TotalTime/1000f);
        startTime = System.currentTimeMillis();

        Main.printMemoryCondition();

        File functionMap = new File(outputPath + "/cloneUnitInfo.csv");
        try{
            BufferedWriter writeText = new BufferedWriter(new FileWriter(functionMap));
            for (Func tmpFunc : data.allFuncs.values()) {
                writeText.write(tmpFunc.funcID+","+tmpFunc.fileName+","+tmpFunc.startLine+","+tmpFunc.endLine+","+tmpFunc.funcLen);
                tmpFunc.fileName = null;
                writeText.newLine(); 
            }
            writeText.flush();
            writeText.close();
        }catch (FileNotFoundException e){
            System.out.println("Don't find cloneUnitInfo.csv file!");
        }catch (IOException e){
            System.out.println("File write/read error!");
        }

        Main.printMemoryCondition();

        //get type1 and type2 clone pairs, T1/T2 Clone Detection
        List<Map.Entry<Integer, Long>> entryList1 = new ArrayList<Map.Entry<Integer, Long>>(data.nonNormHash.entrySet());
        Collections.sort(entryList1, new Comparator<Map.Entry<Integer, Long>>() {
            @Override
            public int compare(Entry<Integer, Long> me1, Entry<Integer, Long> me2) {
                return me1.getValue().compareTo(me2.getValue()); // Ascending sort
            }
        });

        List<Map.Entry<Integer, Long>> entryList2 = new ArrayList<Map.Entry<Integer, Long>>(data.normHash.entrySet());
        Collections.sort(entryList2, new Comparator<Map.Entry<Integer, Long>>() {
            @Override
            public int compare(Entry<Integer, Long> me1, Entry<Integer, Long> me2) {
                return me1.getValue().compareTo(me2.getValue()); // Ascending sort
            }
        });

        Map<Integer, HashSet<Integer>> type1ClonePair = new HashMap<>();
        long tempHash = entryList1.get(0).getValue();
        int tempFuncId = entryList1.get(0).getKey();
        HashSet<Integer> tempHashSet = new HashSet<>();
        for (int i = 1; i < entryList1.size(); i++) {
            if(entryList1.get(i).getValue() == tempHash) {
                tempHashSet.add(entryList1.get(i).getKey());
            } else {
                type1ClonePair.put(tempFuncId, tempHashSet);
                tempFuncId = entryList1.get(i).getKey();
                tempHash = entryList1.get(i).getValue();
                tempHashSet = new HashSet<>();
            }
        }
        int allFuncsNum = data.allFuncs.size();
        Map<Integer, HashSet<Integer>> type2ClonePair = new HashMap<>();
        long tempHash2 = entryList2.get(0).getValue();
        int tempFuncId2 = entryList2.get(0).getKey();
        HashSet<Integer> tempHashSet2 = new HashSet<>();
        for (int i = 1; i < entryList2.size(); i++) {
            if(entryList2.get(i).getValue() == tempHash2) {
                tempHashSet2.add(entryList2.get(i).getKey());
                data.allFuncs.remove(entryList2.get(i).getKey());
            } else {
                type2ClonePair.put(tempFuncId2, tempHashSet2);
                data.needType3Set.add(tempFuncId2);
                tempFuncId2 = entryList2.get(i).getKey();
                tempHash2 = entryList2.get(i).getValue();
                tempHashSet2 = new HashSet<>();
            }
        }
        data.needType3Set.add(tempFuncId2);
        entryList1.clear();
        entryList1 = null;
        entryList2.clear();
        entryList2 = null;
        //output T1 and T2 clone
        int t1CloneNum = 0;
        for (Map.Entry<Integer, HashSet<Integer>> entry : type1ClonePair.entrySet()) {
            t1CloneNum += entry.getValue().size();
        }
        int t2CloneNum = 0;
        for (Map.Entry<Integer, HashSet<Integer>> entry : type2ClonePair.entrySet()) {
            int zuSize = entry.getValue().size();
            t2CloneNum += (zuSize * (zuSize - 1))/2;
        }
        System.out.println("Type1 clone num: "+t1CloneNum);
        System.out.println("Type2 clone num: "+t2CloneNum);
        System.out.println("need T3 verify num: "+data.needType3Set.size());
        File T1writeFile = new File(outputPath+"/T1.csv");
        try{
            BufferedWriter writeText = new BufferedWriter(new FileWriter(T1writeFile));

            for (Map.Entry<Integer, HashSet<Integer>> entry : type1ClonePair.entrySet()) {
                int aId = entry.getKey();
                for (int bId: entry.getValue()) {
                    writeText.write(aId+","+bId);
                    writeText.newLine(); 
                }
            }
            writeText.flush();
            writeText.close();
        }catch (FileNotFoundException e){
            System.out.println("Don't find T1.csv file!");
        }catch (IOException e){
            System.out.println("File write/read error!");
        }

        File T2writeFile = new File(outputPath+"/T2.csv");
        try{
            BufferedWriter writeText = new BufferedWriter(new FileWriter(T2writeFile));
            for (Map.Entry<Integer, HashSet<Integer>> entry : type2ClonePair.entrySet()) {
                int aId = entry.getKey();
                for (int bId: entry.getValue()) {
                    writeText.write(aId+","+bId);
                    writeText.newLine(); 
                }
            }
            writeText.flush();
            writeText.close();
        }catch (FileNotFoundException e){
            System.out.println("Don't find T2.csv file!");
        }catch (IOException e){
            System.out.println("File write/read error!");
        }

        type1ClonePair.clear();
        type1ClonePair = null;
        type2ClonePair.clear();
        type2ClonePair = null;

        


        long pairsNumber = 0;

        
        int partitionSize = (allFuncsNum + partitionNum - 1)/partitionNum;
        
        for (int i = 0; i < partitionNum; i++) {
            int startIndex = i*partitionSize;
            int endIndex = Math.min(allFuncsNum, startIndex + partitionSize);
            // Creat Inverted Index
            Map<Integer, HashSet<Integer>> invertedIndex = new HashMap<>();
            for (int j = startIndex; j < endIndex; j++) {
                if(!data.needType3Set.contains(j) || data.allFuncs.get(j) == null) {
                    continue;
                }
                List<Integer> NGrams = data.allFuncs.get(j).nGramSequences;
                for (int nGram: NGrams){
                    if (invertedIndex.containsKey(nGram)){
                        invertedIndex.get(nGram).add(j);
                    } else {
                        HashSet<Integer> tmpSet = new HashSet<>();
                        tmpSet.add(j);
                        invertedIndex.put(nGram, tmpSet);
                    }
                }
            }
            data.invertedIndex = invertedIndex;


            System.out.println("the "+i+"st inverted index size is "+data.invertedIndex.size());
            Main.printMemoryCondition();
            int funcNum = allFuncsNum - startIndex;
            int funcBlock = (funcNum+threadNum-1)/threadNum;
            CloneDetector[] ccThread = new CloneDetector[threadNum];
            final CountDownLatch latch2 = new CountDownLatch(threadNum);
            for (int j = 0; j < threadNum-1; j++) {
                ccThread[j] = new CloneDetector(startIndex+j*funcBlock, Math.min(startIndex+(j+1)*funcBlock, allFuncsNum), data, latch2);
                ccThread[j].t.start();
            }
            ccThread[threadNum-1] = new CloneDetector(startIndex+(threadNum-1)*funcBlock, allFuncsNum, data, latch2);
            ccThread[threadNum-1].t.start();
            try {
                latch2.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("the "+i+"st clone detect done!");
            int num = 0;
            for (HashSet<Integer> v : data.clonePairs.values()) {
                num += v.size();
            }
            System.out.println("clone pairs number:"+num);
            pairsNumber += num;

            File writeFile = new File(outputPath+"T3_"+i+".csv");

            try{
                BufferedWriter writeText = new BufferedWriter(new FileWriter(writeFile));

                for (Map.Entry<Integer, HashSet<Integer>> entry : data.clonePairs.entrySet()) {
                    Func af = data.allFuncs.get(entry.getKey());
                    for (int b: entry.getValue()) {
                        Func bf = data.allFuncs.get(b);
                        writeText.write(af.funcID+","+bf.funcID);
                        writeText.newLine(); 
                    }
                }
               
                writeText.flush();
                writeText.close();
            }catch (FileNotFoundException e){
                System.out.println("Don't find cloneUnitInfo.csv file!");
            }catch (IOException e){
                System.out.println("File write/read error!");
            }

            data.clonePairs = new HashMap<>();

        }

        endTime   = System.currentTimeMillis();
        TotalTime = endTime - startTime;
        System.out.println("Clone detect time: "+TotalTime/1000f);

        System.out.println("All clone pairs number is:"+pairsNumber);
        
    }

    private static void printMemoryCondition() {
        System.out.println("Memory consumption"+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024/1024); 
        System.out.println("max memory"+Runtime.getRuntime().maxMemory()/1024/1024); 
        System.out.println("total memory"+Runtime.getRuntime().totalMemory()/1024/1024); 
        System.out.println("free memory"+Runtime.getRuntime().freeMemory()/1024/1024); 
    }

    private static void setParameter(String[] args) {
        int lens = args.length;
        if(lens %2 != 0) {
            System.out.println("parameter error! please start with '-'!");
            System.exit(0);
        }
        for (int i = 0; i < lens; i+=2) {
            String arg = args[i];
            if(arg.charAt(0) != '-'){
                System.out.println("parament error!");
                System.exit(0);
            } 
            switch(arg) {
                case "-input":
                    projectPath  = args[i+1];
                    break;
                case "-output":
                    if(args[i+1].charAt(args[i+1].length() - 1) == '/' || args[i+1].charAt(args[i+1].length() - 1) == '\\') {
                        outputPath  = args[i+1];
                    } else {
                        System.out.println("output should end with '/' or '\\'!");
                        System.exit(0);
                    }
                    break;
                case "-partition":
                    try{
                        partitionNum = Integer.parseInt(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("partition is not a integer!");
                        System.exit(0);
                    }
                    break;
                case "-N":
                    try{
                        N = Integer.parseInt(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("N is not a integer!");
                        System.exit(0);
                    }
                    break;
                case "-thread":
                    try{
                        threadNum = Integer.parseInt(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("thread is not a integer!");
                        System.exit(0);
                    }
                    break;
                case "-t1":
                    try{
                        Data.t1_score = Float.parseFloat(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("t1 is not a float!");
                        System.exit(0);
                    }
                    break;
                case "-t2":
                    try{
                        Data.t2_score = Float.parseFloat(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("t2 is not a float!");
                        System.exit(0);
                    }
                    break;
                case "-v1":
                    try{
                        Data.v1_score = Float.parseFloat(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("v1 is not a float!");
                        System.exit(0);
                    }
                    break;
                case "-v2":
                    try{
                        Data.v2_score = Float.parseFloat(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("v2 is not a float!");
                        System.exit(0);
                    }
                    break;
                case "-v3":
                    try{
                        Data.v3_score = Float.parseFloat(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("v3 is not a float!");
                        System.exit(0);
                    }
                    break;
                case "-lv":
                    try{
                        Data.cloneLevel = Integer.parseInt(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("clone level is not an integer!");
                        System.exit(0);
                    }
                    break;
                case "-ml":
                    try{
                        Data.minLine_num = Integer.parseInt(args[i+1]);
                    } catch(Exception e) {
                        System.out.println("min line num is not an integer!");
                        System.exit(0);
                    }
                    break;
                default:
                    System.out.println("unkown parameter");
                    System.exit(0);
            }
        }
        if (projectPath == null) {
            System.out.println("Project path is null!");
            System.exit(0);
        }
    }
}
