package clonedetect;
import myutils.Func;

import java.util.*;
import java.util.concurrent.CountDownLatch;

public class CloneDetector extends Thread{

    private Data data;
    public Thread t;
    int start_func;
    int end_func;
    private final CountDownLatch latch;
    //private Map<Integer, Map<Integer, Double>> edgeScores = new HashMap<>();

    public CloneDetector(int start_func, int end_func, Data data, CountDownLatch latch){
        this.start_func = start_func;
        this.end_func = end_func;
        this.data = data;
        this.latch = latch;
        t = new Thread(this, "pre"+start_func);
    }

    public HashSet<Integer> getClonePair(int funcID){
        Func tmpFunc = data.allFuncs.get(funcID);

        // locate phase, collision list
        HashSet<Integer> cloneCandidate = locatePhase(tmpFunc);
        // if(data.allFuncs.getOrDefault(funcID, null) == null) {
        //     return cloneCandidate;
        // }
        // Ast verification is not required
        HashSet<Integer> notNeedASTVerify = new HashSet<>();
        // filter phase, LVMapper Filter
        HashSet<Integer> removeEle = NLineFilter(cloneCandidate, funcID, notNeedASTVerify);
        // Candidate List1
        cloneCandidate.removeAll(removeEle);
        cloneCandidate.removeAll(notNeedASTVerify);
        // verify phase
        // SourcererCC Filter
        removeEle = sourcererCCVerify(cloneCandidate, funcID, notNeedASTVerify);
        // Candidate List2
        cloneCandidate.removeAll(removeEle);
        cloneCandidate.removeAll(notNeedASTVerify);
        //Subtree matching
        removeEle = astVerify(cloneCandidate, funcID);
        cloneCandidate.removeAll(removeEle);
        cloneCandidate.addAll(notNeedASTVerify);
        return cloneCandidate;
    }
    //locate
    public HashSet<Integer> locatePhase(Func func) {
        HashSet<Integer> cc = new HashSet<>();
        if(func == null || func.nGramSequences == null) {
            return cc;
        }
        for (int ngramHash: func.nGramSequences) {
            if (data.invertedIndex.containsKey(ngramHash)) {
                cc.addAll(data.invertedIndex.get(ngramHash));
            }
        }
        return cc;
    }

    // N-Line-based Filter
    public HashSet<Integer> NLineFilter(HashSet<Integer> cloneCandidate, int c_block_index, HashSet<Integer> notNeedASTVerify) {
        HashSet<Integer> remove_ele = new HashSet<>();
        for (int b_block_index : cloneCandidate) {
            if (b_block_index >= c_block_index) {
                remove_ele.add(b_block_index);
                continue;
            }
            Func b_block = data.allFuncs.get(b_block_index);
            Func c_block = data.allFuncs.get(c_block_index);
            int common_ngram = getCommonNGram(b_block_index, c_block.nGramSequences);
            int max_len = Math.max(c_block.funcLen, b_block.funcLen) - data.N + 1;
//If only considering code with a length greater than 6, this step can be omitted
//            if (min_len == 0) {
//                min_len = 10;
//            }
            float filterSim = 1.0f*common_ngram/max_len;
            if (filterSim < Data.t1_score) {
                remove_ele.add(b_block_index);
            } 
            else if(filterSim > Data.v1_score) {
                notNeedASTVerify.add(b_block_index);
            }
        }
        return remove_ele;
    }

    // SourcererCC Filter
    public HashSet<Integer> sourcererCCVerify(HashSet<Integer> cloneCandidate, int c_block_index, HashSet<Integer> notNeedASTVerify) {
        HashSet<Integer> remove_ele = new HashSet<>();
        for (int b_block_index : cloneCandidate) {

            List<Integer> b_block = data.allFuncs.get(b_block_index).divTokenSequence;
            List<Integer> c_block = data.allFuncs.get(c_block_index).divTokenSequence;

            int ct = (int)Math.ceil(Math.max(b_block.size(), c_block.size())*Data.t2_score);
            //Greater than \theta does not require further verification
            int mt = (int)Math.ceil(Math.max(b_block.size(), c_block.size())*Data.v2_score);
            int overlapTokenNum = overlapNum(b_block, c_block);
            if (overlapTokenNum < ct) {
                remove_ele.add(b_block_index);
            } else if (overlapTokenNum > mt) {
                notNeedASTVerify.add(b_block_index);
            }
        }
        return remove_ele;
    }
    
    // ast verify
    private HashSet<Integer> astVerify(HashSet<Integer> cloneCandidate, int c_block_index) {
        HashSet<Integer> removeEle = new HashSet<>();
        Func cFunc = data.allFuncs.get(c_block_index);
        Map<Integer, Short> hashCnt1 = cFunc.astNodeHashCnt;
        int cAstNodeNum = cFunc.astNodeNum;
        for (int b_block_index : cloneCandidate) {
            Func bFunc = data.allFuncs.get(b_block_index);
            Map<Integer, Short> hashCnt2 = bFunc.astNodeHashCnt;
            int bAstNodeNum = bFunc.astNodeNum;
            int overlap = 0;
            for (Map.Entry<Integer, Short> entry: hashCnt1.entrySet()){
                if (hashCnt2.containsKey(entry.getKey())) {
                    overlap += Math.min(entry.getValue(), hashCnt2.get(entry.getKey()));
                }
            }
            double score = 1.0*overlap/Math.max(cAstNodeNum, bAstNodeNum);
            if (score < Data.v3_score) {
                removeEle.add(b_block_index);
            }
        }
        return removeEle;
    }

    
    public int overlapNum(List<Integer> ls1, List<Integer> ls2) {
        int res = 0;
        int len1 = ls1.size(), len2 = ls2.size();
        int i1=0, i2=0;
        while (i1<len1 && i2<len2) {
            int tmp1 = ls1.get(i1), tmp2 = ls2.get(i2);
            if (tmp1 == tmp2){
                res++;
                i1++;
                i2++;
            } else {
                if (tmp1 < tmp2) {
                    i1++;
                } else {
                    i2++;
                }
            }
        }
        return res;
    }

    public int getCommonNGram(int b_block_index, List<Integer> c_ngram){
        int res = 0;
        for (int ngramHash : c_ngram) {
            if (data.invertedIndex.containsKey(ngramHash) && data.invertedIndex.get(ngramHash).contains(b_block_index)) {
                res++;
            }
        }
        return res;
    }



    @Override
    public void run() {
        //HashMap<Integer, HashSet<Integer>> tmpClonePairs = new HashMap<>();
        for(int i=start_func; i < end_func; i++) {
            if(!data.needType3Set.contains(i)) 
                continue;
            HashSet<Integer> tmpSet = getClonePair(i);
            if (tmpSet.size() > 0) {
                if(data.clonePairs.containsKey(i)) {
                    data.clonePairs.get(i).addAll(tmpSet);
                }
                else {
                    data.clonePairs.put(i, tmpSet);
                }
            }
        }
        latch.countDown();
    }


}
