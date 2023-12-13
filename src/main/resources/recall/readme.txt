数据库选取：00,0a,0b,0c,0d,0e,0f,01,1a,1b,1c,1d,1e,1f,02,2a,2b,2c,2d,2e,2f
测试集：03前100个.sol文件

xxxInfo.csv:(xxx为func、contract、file)
xxxID, fileID, 开始行, 结束行, 未归一化hash, 归一化hash, tokens info, ast info

filePath.csv:
fileID, file path

clone_xxx.csv
测试文件路径, 测试文件开始行, 测试文件结束行, 数据库文件ID, 克隆类型, token相似度, ast相似度