---
date: 2019/3/1 21:00:00
---

# 读dirsearch有感

最近开始阅读一些优秀的开源项目，读完之后就顺手写写读后感吧，今天说说dirsearch，开头嘛，先读个简单点的，哈哈。

## 1.解决Debug

程序参数是从命令行获取的，而debug模式则是将一个py脚本作为程序入口的，笔者开始有点不知所措，突然临机一动，用下面这个代码不就解决了？

```
import dirsearch
import sys
sys.argv = ['dirsearch.py', '-u', 'http://192.168.1.55', '-e', 'jsp', '--proxy', 'http://127.0.0.1:18080', '-w', 'db/dicc_test.txt']
dirsearch.Program()
```

## 2.简说程序

dirsearch是一款使用python3编写的，用于暴力破解目录的工具，其README有写到下面一点

- Heuristically detects invalid web pages（启发式地检测无效web页面）

在读程序之前，我带着下面几点疑惑/好奇

1.它是如果做到”启发式“这一点的；

2.其线程方面的代码，是否有什么亮点；

3.有啥比较骚的功能不。

### 2.1 dirsearch的启发式

从底层核心类开始说起。首先是Scanner，主要用于分析并存储当前网站对各类无效目录/无效文件真正的HTTP Resonse的模式。

Scanner在测试时，使用的路径/文件是一个包含12个随机字符的字符串，如68yK0OccrHpt、68yK0OccrHpt.php

```
self.testPath = RandomUtils.randString()
```

有的网站系统，对请求无效WEB页面的HTTP Request，返回的是200的状态码，当然，界面是一个友好界面；有的网站则会返回一个301/302/307的跳转。

当Scanner访问这些随机字符串路径时，如果服务器返回的状态码是404，则Scanner不继续分析，直接返回；服务器返回的状态码不是404，Scanner会发送第二次请求，依然是随机字符串的路径/文件，之后分析两次Response Body的相似度并保存该相似度的浮点值，如果两次Response都发生了跳转（301/302/307），那么还会为Location字段值（URL）生成一个正则，如下面所示。

```
from difflib import SequenceMatcher
import re
def generateRedirectRegExp(firstLocation, secondLocation):
    if firstLocation is None or secondLocation is None:
        return None

    sm = SequenceMatcher(None, firstLocation, secondLocation)
    marks = []

    for blocks in sm.get_matching_blocks():
        i = blocks[0]
        n = blocks[2]
        # empty block

        if n == 0:
            continue

        mark = firstLocation[i:i + n]
        marks.append(mark)

>>generateRedirectRegExp("http://www.test.com","http://123.test.com"))
>> ^.*http\:\/\/.*\.test\.com.*$
```

之后访问一个目录/文件时，发生跳转中的Location的值需要匹配该正则，也页面相似度不小于当前值时，该目录/文件才被认为是无效的。可以说，dirsearch在这里做得很细致啊。

Scanner是被Fuzzer创建并调用的，Fuzzer为无后缀斜线目录（/dir）、有后缀斜线目录（/dir/）、用户指定扩展文件（/xx.php、/xx.jsp等）分别创建了一个Scanner，Scanner在执行setup()函数时，会如本节开头所说的，会分析出该种目录/文件的无效目录/无效文件所对应的HTTP Response的模式。

### 2.2 dirsearch的多线程

有人说dirsearch速度很快，笔者以为在多线程方面会有亮点，比如说用协程，但并没有啥亮点。

虽然有GIL这东西然人感到不舒服，但也习惯了用threading，编写起来也快，笔者想着下一个项目还是得要求自己用用协程。

### 2.3 dirsearch的迭代遍历

–recursive用于递归目录遍历，默认是关闭的，而设置该选项时，还可以设置–exclude-subdi排除不想做迭代的目录。

在Controller中设置了matchCallbacks函数，该函数会将当前有效的不在exclude中的目录添加到当前

```
self.fuzzer = Fuzzer(self.requester, self.dictionary, testFailPath=self.arguments.testFailPath,
                     threads=self.arguments.threadsCount, matchCallbacks=matchCallbacks,
                     notFoundCallbacks=notFoundCallbacks, errorCallbacks=errorCallbacks)
```

在测试该功能时还发现了一个BUG，已经提交至Issues。问题的来源是这样的，Requester中有这样一行代码

```
url = urllib.parse.urljoin(url, self.basePath)
```

但是，这个urllib库者urljoin函数有点问题。

```
>>>from urllib import parse
>>>parse.urljoin("http://192.168.237.136","//admin/")
>>>'http://admin/'
```

笔者提交的修补代码是。

```
while True:
	path_tmp = self.basePath.replace('//', '/')
	if path_tmp == self.basePath:
		break
	self.basePath = path_tmp
```

比较郁闷的一点时，dirsearch对待wordlist中结尾有“/”的，且该目录在当前目标URL中为有效时才会进行迭代遍历，比如

访问<http://www.test.com/admin> 时的 HTTP Response状态码为200 ，dirsearch不会对该目录进行迭代遍历，访问<http://www.test.com/admin/> 时的 HTTP Response 状态码200，dirsearch会对该目录进行迭代遍历。为啥要把选择权交给wordlist，作者为啥要做这种区分，吾不知所以然啊。

### 2.4 IP选项

在渗透测试时，有时候做目录遍历时，不得不只能能用BurpSuit，不知道同学们对此是否有所体会。其中的痛点需求是，我们希望底层的Socket连接的是一个指定的IP，然后HTTP中的Host字段值则是另外一个指定的域名/IP。

dirsearch就很巧妙地解决了以上痛点需求。requests传入的URL中的host值是来源于`-ip`,另外设置Headers中的`Host`字段值为–url中的值。

## 3.不足之处

### 3.1

笔者十分在意的另外一点是，dirsearch在扫目录时，没有主动区分”/dir”、“/dir/”，这两类目录（当然，前文也说了，作者把这两类作为是否迭代遍历的标志）。笔者的意思是，有时候，访问“[http://www.test.com/admin"，HTTP](http://www.test.com/admin%22%EF%BC%8CHTTP) Response状态码为301；访问“[http://www.test.com/admin/"，HTTP](http://www.test.com/admin/%22%EF%BC%8CHTTP) Response状态码为404。所以笔者十分在意这一点，这也是一个痛点啊，dirsearch并没有对此做一个主动性的区分。

### 3.2

还有一点可能有点吹毛求疵了，在读取字典时，不能将一个目录作为读入点。

结尾，dirsearch的`启发式识别URL是否有效`确实挺不错的，可能是不同人有不同的想法，所以项目的一些地方会让笔者感到疑惑不解，而项目的整体逻辑也挺不错，适合像笔者这样的初学者好好看，好好学。本文也作为笔者的备忘录

[展开全文 >>](https://turn1tup.github.io/2018/12/09/%E8%AF%BBdirsearch%E6%9C%89%E6%84%9F/)