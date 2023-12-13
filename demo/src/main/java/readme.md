18w函数：java Main2 -input /bdata2/wyk/smartcontracts/dataset/extraction/celo/functions/ -lv 0 

80w函数：java Main2 -input /bdata2/wyk/smartcontracts/dataset/extraction/tron/functions/

140M：java -Xms60720M -Xmx200880M Main2 -input /bdata/wyk/SmartContract/dataset/smart-contract-sanctuary/ethereum/contracts/mainnet/ -ml 1 -N 1 -lv 2 -partition 1000 -output /bdata2/yyh/demo/src/main/resources/output/

contract level：java -Xms60720M -Xmx200880M Main2 -input /bdata/wyk/SmartContract/dataset/smart-contract-sanctuary/ethereum/contracts/mainnet/ -ml 3 -N 3 -lv 1 -partition 1000 -output /bdata2/yyh/demo/src/main/resources/output/

non_bsc_ethereum: java -Xms60720M -Xmx200880M Main2 -input /bdata/wyk/SmartContract/dataset/smart-contract-sanctuary/ -ml 10 -N 5 -lv 1 -partition 1000 -output /bdata2/yyh/output/non_bsc_ethereum/