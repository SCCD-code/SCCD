package clonedetect;

import myutils.Func;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.io.IOException;
import java.nio.ByteBuffer;
import com.google.common.hash.Hashing;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.Trees;

import antlr4Sol.SolidityLexer;
import antlr4Sol.SolidityParser;

public class PreProcess extends Thread{

    public Thread t;
    int start_file;
    int end_file;
    Data data;
    private final CountDownLatch latch;
    private Map<Integer, Long> tempNonNormHash = new HashMap<>();
    private Map<Integer, Long> tempNormHash = new HashMap<>();
    private int projectPathLength = 0;
    public PreProcess(int start_file, int end_file, Data data, CountDownLatch latch) {
        this.start_file = start_file;
        this.end_file = end_file;
        this.data = data;
        this.projectPathLength = data.projectPath.length();
        t = new Thread(this, "pre"+start_file);
        this.latch = latch;
    }

    @Override
    public void run(){
        for (int i = start_file; i < end_file; i++) {
            Map<Integer, Func> tmpFuncs = new HashMap<>();
            if(Data.cloneLevel == 0) {
                tmpFuncs = getFileLevelInfo(data.files.get(i));
            } else if (Data.cloneLevel == 1) {
                tmpFuncs = getContractLevelInfo(data.files.get(i));
            } else {
                tmpFuncs = getFuncLevelInfo(data.files.get(i));
            }
            this.data.addFunctions(tmpFuncs);
            this.data.setFuncNGram(tmpFuncs);
            this.data.sortTokens(tmpFuncs);
            //this.data.updateInvertedIndex(tmpFuncs);
            //对token进行排序
            
            // if (i % 10000 == 0) {
            //     System.out.println("File "+i + "processing done!");
            // }
        }
        this.data.addNonNormHash(tempNonNormHash);
        this.data.addNormHash(tempNormHash);
        latch.countDown();
    }

    private Map<Integer, Func> getFileLevelInfo(String filePath) {
        Map<Integer, Func> tempFuncs = new HashMap<>();

        try {
            CharStream input = CharStreams.fromFileName(filePath);
            //make lexer
            Lexer lexer = new SolidityLexer(input);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            tokenStream.fill();
            List<Token> tokens = tokenStream.getTokens(0, tokenStream.size()-1);
            //normalization token blocks
            List<Integer> divTokens = new ArrayList<>();
            //unnormalization token blocks
            List<Integer> normTokens = new ArrayList<>();
            //line blocks, normalization N-Lines Blocks
            List<String> normLines = new ArrayList<>();
            int curLine = tokens.get(0).getLine();
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < tokens.size(); i++) {
                Token tmpToken = tokens.get(i);
                int tempTokenHash = tmpToken.getText().hashCode();
                if(tmpToken.getChannel() == 1) {
                    continue;
                }
                divTokens.add(tempTokenHash);
                normTokens.add(tmpToken.getType());
                if(tmpToken.getLine() == curLine) {
                    //sb.append(tmpToken.getType());
                    sb.append(tempTokenHash);
                } else {
                    normLines.add(sb.toString());
                    sb = new StringBuffer();
                    sb.append(tempTokenHash);
                    curLine = tmpToken.getLine();
                }
                //System.out.println(tokens.get(i).getText()+" // " + tokens.get(i).getType() + " // " + tokens.get(i).toString());
            }
            normLines.add(sb.toString());
            //System.out.println("token num:" + tokens.size());
            // Parse-trees
            SolidityParser parser = new SolidityParser(tokenStream);
            parser.removeErrorListeners();
            parser.addErrorListener(new ANTLRErrorListener() {

                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                        int charPositionInLine, String msg, RecognitionException e) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact,
                        BitSet ambigAlts, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                        BitSet conflictingAlts, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                        int prediction, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }
                
            });
            List<String> ruleNamesList = Arrays.asList(parser.getRuleNames());
            ParseTree tree = parser.sourceUnit();
            Map<Integer, Short> hashCnt = getASTHashDict(tree, ruleNamesList);
            int astNodeNum = 0;
            for (int value : hashCnt.values()) {
                astNodeNum += value;
            }
            //过滤行数小于6行的文件
            if (normLines.size() > Data.minLine_num) {
                Func tmpFunc = new Func();
                tmpFunc.fileName = filePath.substring(projectPathLength);
                tmpFunc.startLine = 0;
                tmpFunc.endLine = (short)curLine;
                tmpFunc.divTokenSequence = divTokens;
                //可以使用murmur3_128位hash来降低hash碰撞的概率
                tempNonNormHash.put(tmpFunc.funcID, getHash64(divTokens));
                tmpFunc.astNodeHashCnt = hashCnt;
                tmpFunc.astNodeNum = (short)astNodeNum;
                tmpFunc.funcLen = (short)normLines.size();
                tmpFunc.normTokenSequence = normLines;
                tempNormHash.put(tmpFunc.funcID, getHash64(normTokens));
                tempFuncs.put(tmpFunc.funcID, tmpFunc);
                if(tmpFunc.funcID %1000 == 0) {
                    System.out.println(tmpFunc.funcID+" || token num:" + divTokens.size() + " || ast node num:"+astNodeNum);
                }
            }
            //System.out.println("ast node num: "+astNodeNum);
            //System.out.println(tree.toStringTree());
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return tempFuncs;
    }

    private Map<Integer, Func> getFuncLevelInfo(String filePath) {
        Map<Integer, Func> tempFuncs = new HashMap<>();
        try {
            CharStream input = CharStreams.fromFileName(filePath);
            //make lexer
            Lexer lexer = new SolidityLexer(input);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            tokenStream.fill();
            List<Token> tokens = tokenStream.getTokens(0, tokenStream.size()-1);
            SolidityParser parser = new SolidityParser(tokenStream);
            parser.removeErrorListeners();
            parser.addErrorListener(new ANTLRErrorListener() {

                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                        int charPositionInLine, String msg, RecognitionException e) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact,
                        BitSet ambigAlts, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                        BitSet conflictingAlts, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                        int prediction, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }
                
            });
            List<String> ruleNamesList = Arrays.asList(parser.getRuleNames());
            ParseTree tree = parser.sourceUnit();

            Queue<ParseTree> visteQueue = new LinkedList<>();
            visteQueue.offer(tree);
            int funcDecHash = "functionDefinition".hashCode();
            while(!visteQueue.isEmpty()) {
                ParseTree firstEle = visteQueue.poll();
                int firstEleHash = Trees.getNodeText(firstEle, ruleNamesList).hashCode();
                if (firstEleHash == funcDecHash) {
                    Interval tmpInterval = firstEle.getSourceInterval();
                    int sIndex = tmpInterval.a;
                    int eIndex = tmpInterval.b;
                    int startLine = tokens.get(sIndex).getLine();
                    int endLine = tokens.get(eIndex).getLine();
                    
                    List<Integer> divTokens = new ArrayList<>();
                    List<Integer> normTokens = new ArrayList<>();
                    List<String> normLines = new ArrayList<>();
                    int curLine = tokens.get(sIndex).getLine();
                    StringBuffer sb = new StringBuffer();
                    for (int i = sIndex; i <= eIndex; i++) {
                        Token tmpToken = tokens.get(i);
                        int tempTokenHash = tmpToken.getText().hashCode();
                        if(tmpToken.getChannel() == 1) {
                            continue;
                        }
                        divTokens.add(tempTokenHash);
                        normTokens.add(tmpToken.getType());
                        if(tmpToken.getLine() == curLine) {
                            //sb.append(tmpToken.getType());
                            sb.append(tempTokenHash);
                        } else {
                            normLines.add(sb.toString());
                            sb = new StringBuffer();
                            //bug fix!
                            sb.append(tempTokenHash);
                            curLine = tmpToken.getLine();
                        }
                    }
                    normLines.add(sb.toString());
                    //过滤小于3行的函数
                    if (normLines.size() < Data.minLine_num) continue;
                    Map<Integer, Short> hashCnt = getASTHashDict(firstEle, ruleNamesList);
                    int astNodeNum = 0;
                    for (int value : hashCnt.values()) {
                        astNodeNum += value;
                    }
                    Func tmpFunc = new Func();
                    tmpFunc.fileName = filePath.substring(projectPathLength);
                    tmpFunc.startLine = (short)startLine;
                    tmpFunc.endLine = (short)endLine;
                    tmpFunc.divTokenSequence = divTokens;
                    //可以使用murmur3_128位hash来降低hash碰撞的概率
                    tempNonNormHash.put(tmpFunc.funcID, getHash64(divTokens));
                    
                    tmpFunc.astNodeHashCnt = hashCnt;
                    tmpFunc.astNodeNum = (short)astNodeNum;
                    tmpFunc.funcLen = (short)normLines.size();
                    tmpFunc.normTokenSequence = normLines;
                    tempNormHash.put(tmpFunc.funcID, getHash64(normTokens));
                    tempFuncs.put(tmpFunc.funcID, tmpFunc);
                    // if(tmpFunc.funcID %100000 == 0) {
                    //     System.out.println(tmpFunc.funcID+" || token num:" + divTokens.size() + " || ast node num:"+astNodeNum);
                    // }
                    
                } else {
                    int tmpChildrenNum = firstEle.getChildCount();
                    for (int i = 0; i < tmpChildrenNum; i++) {
                        if (firstEle.getChild(i).getChildCount() != 0) {
                            visteQueue.offer(firstEle.getChild(i));
                        }
                    }
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return tempFuncs;
    }

    //只保留contarct。丢弃interface和library
    private Map<Integer, Func> getContractLevelInfo(String filePath) {
        Map<Integer, Func> tempFuncs = new HashMap<>();
        try {
            CharStream input = CharStreams.fromFileName(filePath);
            //make lexer
            Lexer lexer = new SolidityLexer(input);
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            tokenStream.fill();
            List<Token> tokens = tokenStream.getTokens(0, tokenStream.size()-1);
            SolidityParser parser = new SolidityParser(tokenStream);
            parser.removeErrorListeners();
            parser.addErrorListener(new ANTLRErrorListener() {

                @Override
                public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                        int charPositionInLine, String msg, RecognitionException e) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact,
                        BitSet ambigAlts, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                        BitSet conflictingAlts, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }

                @Override
                public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
                        int prediction, ATNConfigSet configs) {
                    // TODO Auto-generated method stub
                    
                }
                
            });
            List<String> ruleNamesList = Arrays.asList(parser.getRuleNames());
            ParseTree tree = parser.sourceUnit();

            Queue<ParseTree> visteQueue = new LinkedList<>();
            visteQueue.offer(tree);
            int funcDecHash = "contractDefinition".hashCode();
            int contractHash = "contract".hashCode();
            while(!visteQueue.isEmpty()) {
                ParseTree firstEle = visteQueue.poll();
                int firstEleHash = Trees.getNodeText(firstEle, ruleNamesList).hashCode();
                if (firstEleHash == funcDecHash && Trees.getNodeText(firstEle.getChild(0), ruleNamesList).hashCode() == contractHash) {
                    Interval tmpInterval = firstEle.getSourceInterval();
                    int sIndex = tmpInterval.a;
                    int eIndex = tmpInterval.b;
                    int startLine = tokens.get(sIndex).getLine();
                    int endLine = tokens.get(eIndex).getLine();
                    
                    List<Integer> divTokens = new ArrayList<>();
                    List<Integer> normTokens = new ArrayList<>();
                    List<String> normLines = new ArrayList<>();
                    int curLine = tokens.get(sIndex).getLine();
                    StringBuffer sb = new StringBuffer();
                    for (int i = sIndex; i <= eIndex; i++) {
                        Token tmpToken = tokens.get(i);
                        int tempTokenHash = tmpToken.getText().hashCode();
                        if(tmpToken.getChannel() == 1) {
                            continue;
                        }
                        divTokens.add(tempTokenHash);
                        normTokens.add(tmpToken.getType());
                        if(tmpToken.getLine() == curLine) {
                            //sb.append(tmpToken.getType());
                            sb.append(tempTokenHash);
                        } else {
                            normLines.add(sb.toString());
                            sb = new StringBuffer();
                            sb.append(tempTokenHash);
                            curLine = tmpToken.getLine();
                        }
                    }
                    normLines.add(sb.toString());
                    //过滤小于3行的函数
                    if (normLines.size() < Data.minLine_num) continue;
                    Map<Integer, Short> hashCnt = getASTHashDict(firstEle, ruleNamesList);
                    int astNodeNum = 0;
                    for (int value : hashCnt.values()) {
                        astNodeNum += value;
                    }
                    Func tmpFunc = new Func();
                    tmpFunc.fileName = filePath.substring(projectPathLength);
                    tmpFunc.startLine = (short)startLine;
                    tmpFunc.endLine = (short)endLine;
                    tmpFunc.divTokenSequence = divTokens;
                    //可以使用murmur3_128位hash来降低hash碰撞的概率
                    tempNonNormHash.put(tmpFunc.funcID, getHash64(divTokens));
                    tmpFunc.astNodeHashCnt = hashCnt;
                    tmpFunc.astNodeNum = (short)astNodeNum;
                    tmpFunc.funcLen = (short)normLines.size();
                    tmpFunc.normTokenSequence = normLines;
                    tempNormHash.put(tmpFunc.funcID, getHash64(normTokens));
                    tempFuncs.put(tmpFunc.funcID, tmpFunc);
                    if(tmpFunc.funcID %100000 == 0) {
                        System.out.println(tmpFunc.funcID+" || token num:" + divTokens.size() + " || ast node num:"+astNodeNum);
                    }
                    
                } else {
                    int tmpChildrenNum = firstEle.getChildCount();
                    for (int i = 0; i < tmpChildrenNum; i++) {
                        if (firstEle.getChild(i).getChildCount() != 0) {
                            visteQueue.offer(firstEle.getChild(i));
                        }
                    }
                }
            }
        } catch (IOException e) {
            //e.printStackTrace();
        }
        return tempFuncs;
    }

    //Parser-tree Build
    private Map<Integer, Short> getASTHashDict(ParseTree cu, List<String> ruleNamesList) {
        Deque<ParseTree> stack = new ArrayDeque<>();
        Map<ParseTree, Integer> visited = new HashMap<>();
        //Map<Node, Integer> nodeHeight = new HashMap<>();
        stack.push(cu);
        //nodeHeight.put(cu, 1); //存放节点权重: 2^q - 1
        while (!stack.isEmpty()) {
            ParseTree node = stack.peek();
            //int h = nodeHeight.get(node);
            int childNodeNum = node.getChildCount();
            /* 如果当前节点为叶子节点或者当前节点的子节点已经遍历过 */
            if (visited.containsKey(node)) {
                stack.pop();
                String nodeText = Trees.getNodeText(node, ruleNamesList);
                //System.out.println(nodeText);
                int tmpHash = nodeText.hashCode();
                for (int i = 0; i < childNodeNum; i++) {
                    tmpHash += visited.getOrDefault(node.getChild(i), 0);
                }
                visited.put(node, tmpHash);
                continue;
            } else if (childNodeNum == 0){
                stack.pop();
                visited.put(node, Trees.getNodeText(node, ruleNamesList).hashCode());
                continue;
            }
            for (int i = childNodeNum - 1; i >= 0; --i) {
                stack.push(node.getChild(i));
            }
            visited.put(node, 0);
        }
        // Hash Trees
        Map<Integer, Short> hashCount = new HashMap<>();
        short defualtValue = 0;
        for (int v: visited.values()) {
            hashCount.put(v, (short)(hashCount.getOrDefault(v, defualtValue)+1));
        }
        return hashCount;
    }


    private long getHash64(List<Integer> myList) {
        ByteBuffer buffer = ByteBuffer.allocate(myList.size() * 8);
        myList.forEach(s -> buffer.putLong(s.hashCode()));
        byte[] bytes = buffer.array();

        // Hash the byte array using the MurmurHash3 algorithm
        long hashcode = Hashing.murmur3_128().hashBytes(bytes).asLong();
        return hashcode;
    }

}
