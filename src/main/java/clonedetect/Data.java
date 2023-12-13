package clonedetect;
import myutils.Func;

// import java.io.BufferedReader;
// import java.io.FileReader;
// import java.io.IOException;
import java.util.*;

public class Data {
    public Map<Integer, HashSet<Integer>> invertedIndex;
    public Map<Integer, Func> allFuncs;
    public int N;
    public String projectPath;
    public ArrayList<String> files;
    public static float t1_score = 0.1f;
    public static float t2_score = 0.2f;
    public static float v1_score = 0.6f;
    public static float v2_score = 0.7f;
    public static float v3_score = 0.65f;
    public static int minLine_num = 20;
    public static int cloneLevel = 0;
    //public Map<String, Integer> GPT;
    public Map<Integer, HashSet<Integer>> clonePairs;
    public Map<Integer, Long> nonNormHash;
    public Map<Integer, Long> normHash;
    public Set<Integer> needType3Set;




    public Data(int N, String projectPath, ArrayList<String> files) {

        invertedIndex = new HashMap<>();
        allFuncs = new HashMap<>();
        this.N = N;
        this.projectPath = projectPath;
        this.files = files;
        this.clonePairs = new HashMap<>();
        this.nonNormHash = new HashMap<>();
        this.normHash = new HashMap<>();
        this.needType3Set = new HashSet<>();

    }


    public void addFunctions(Map<Integer, Func> funcs) {
        allFuncs.putAll(funcs);
    }

    public void addNonNormHash(Map<Integer, Long> hashs) {
        nonNormHash.putAll(hashs);
    }

    public void addNormHash(Map<Integer, Long> hashs) {
        normHash.putAll(hashs);
    }

    public void updateInvertedIndex(Map<Integer, Func> funcs) {
        for (Map.Entry<Integer, Func> entry: funcs.entrySet()) {
            int k = entry.getKey();
            Func func = entry.getValue();
            for (int hashStr: func.nGramSequences) {
                if(invertedIndex.containsKey(hashStr)) {
                    invertedIndex.getOrDefault(hashStr, new HashSet<Integer>()).add(k);
                } else {
                    HashSet<Integer> tmpLS = new HashSet<>();
                    tmpLS.add(k);
                    invertedIndex.put(hashStr, tmpLS);
                }
            }
        }
    }

    public void setFuncNGram(Map<Integer, Func> funcs) {
        for (Func func: funcs.values()) {
            List<String> tokenSequence = func.normTokenSequence;
            int len = tokenSequence.size() - N + 1;
            for (int i = 0; i < len; i++) {
                StringBuffer temp_str = new StringBuffer();
                for (int j = 0; j < N; j++) {
                    temp_str.append(tokenSequence.get(i + j));
                }
                //func.nGramSequences.add(Hashing.murmur3_128().hashBytes(temp_str.getBytes()).toString());
                func.nGramSequences.add(temp_str.toString().hashCode());
            }
            func.normTokenSequence = null;
            //System.out.println(func.funcID+"  "+func.nGramSequences);
        }
    }

    
    public void sortTokens(Map<Integer, Func> funcs) {
        for (Func func: funcs.values()) {
            Collections.sort(func.divTokenSequence);
            // for (String token : func.divTokenSequence) {
            //     GPT.put(token, GPT.getOrDefault(token, 0)+1);
            // }
        }
    }



}
