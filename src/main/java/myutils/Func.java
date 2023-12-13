package myutils;


import java.util.*;
public class Func {
    public short startLine;
    public short endLine;
    public List<String> normTokenSequence;
    public String fileName;

    public int fileID;
    
    public int funcID;
    public List<Integer> nGramSequences;
    public Map<Integer, Short> astNodeHashCnt;
    public List<Integer> divTokenSequence;
    private static int funcCount = 0;
    public short astNodeNum = 1;
    public boolean astNotVisted = true;
    public short funcLen = 1;
    //public int[] edgeInfoArr;
    public Func() {
        funcID = funcCount++;
        nGramSequences = new ArrayList<>();
        divTokenSequence = new ArrayList<>();
        normTokenSequence = new ArrayList<>();
        astNodeHashCnt = new HashMap<>();
    }

    public int getFuncLen(){
        return endLine - startLine;
    }


}
