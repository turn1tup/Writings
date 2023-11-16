---
date: 2019/10/3 21:00:00
---

## 引言

之前看了看Apache是如何解析HTTP请求的，整理了一下笔记，说说其中的一些要点。

首先，在最新版本的Apache服务器http2.4.41中，似乎移除了对HTTP的许多“兼容特性”，这些特性是不符合RFC标准的，而笔者当时阅读的代码为2.4.3，本文记录了笔者在阅读代码时认为十分重要的一些解析细节。

## read a line

Apache读到LF字符时，就判断一行已经读完了。

![1575636988533](简析Apache如何解析HTTP请求/1575636988533.png)

![1575637029446](简析Apache如何解析HTTP请求/1575637029446.png)



## before request line

读取请求行时，会跳过blank line，默认为DEFAULT_LIMIT_BLANK_LINES 次 （limit_req_fields没有被初始化），奇怪的是在2.4.3源码中没有搜索到定义值，在2.4.41中倒是搜索到了。

Apache之所以会这样做，代码中解释说，浏览器在发POST请求时，会在末尾添加CRLF，如果形成一个pileline请求，request line前面就会有空行。

![1575637431252](简析Apache如何解析HTTP请求/1575637431252.png)

![1575637515203](简析Apache如何解析HTTP请求/1575637515203.png)



![1575637273820](简析Apache如何解析HTTP请求/1575637273820.png)

## request line

请求行默认长度最大为8190字节，请求行由三部分组成 method、uri、version

![1575636873879](简析Apache如何解析HTTP请求/1575636873879.png)

![1575637842396](简析Apache如何解析HTTP请求/1575637842396.png)



在获取method 、uri的时候，需要判断是否空白字符，2.4.3使用isspace来判断是否空白字符。

![1575638543636](简析Apache如何解析HTTP请求/1575638543636.png)



![1575638436652](简析Apache如何解析HTTP请求/1575638436652.png)

内置method有26个，未被识别时返回UNKNOWN_METHOD（为啥phpstudy搭建的2.4.23 瞎写method都可以...）

![1575639434615](简析Apache如何解析HTTP请求/1575639434615.png)

![1575639557902](简析Apache如何解析HTTP请求/1575639557902.png)

![1575639039658](简析Apache如何解析HTTP请求/1575639039658.png)

version没被解析成功的情况下，被设置为HTTP1.0

![1575638751179](简析Apache如何解析HTTP请求/1575638751179.png)



URI是如何解析的，没有看到具体代码，相应的函数是个钩子函数。

![1575641409296](简析Apache如何解析HTTP请求/1575641409296.png)

在URL路径中，即？前面的字符串中，如果存在畸形URL编码，如%fg，则直接返回400；如果存在0字符，则返回404

![1575641491019](简析Apache如何解析HTTP请求/1575641491019.png)

## header filed

每个头部默认长度最大为8190字节，头部个数最大默认为100

![1575642413511](简析Apache如何解析HTTP请求/1575642413511.png)

![1575642630350](简析Apache如何解析HTTP请求/1575642630350.png)

如果一个头部以SP或HT字符开头，表示该头部是上一个头部的extended，或者说是一个continue header line，该行将被合并到上一行，符合RFC。

![img](简析Apache如何解析HTTP请求/0570bbebcd774693909db21e89a21da2.png)

![img](简析Apache如何解析HTTP请求/d1733fc5e392425f9081dc7d3e1b31ff.png)

请求域行中必须要有冒号 :

![img](简析Apache如何解析HTTP请求/5f8b0bd018e44015b9ca8e822a692680.png)

头部中的多个LWS字符会被跳过。

![img](简析Apache如何解析HTTP请求/264ae11b938641b1b83bb9402035693d.png)

相同头部字段名的头部行将被合并，缓冲区大小不变

![img](简析Apache如何解析HTTP请求/ae2506ae48ab4c329bda241f1c261d88.png)

## TE

Transfer-Encoding存在时忽略Content-Length

![1575642736359](简析Apache如何解析HTTP请求/1575642736359.png)

TE头部的值必须为chunked

![1575647450202](简析Apache如何解析HTTP请求/1575647450202.png)

## must hostname

http 1.1必须要有host头部字段，否则返回400

![img](简析Apache如何解析HTTP请求/908d16b3e233405abbdba1fc3360eeac.png)

当Content-Type不为空且前面33个字符值为application/x-www-form-urlencoded时，会将Body认为为表单，并以"&"符号分割表单，“=”区分key value，并且会对二者都进行URL解码，详细代码冗长，就不贴出来了。

![1575646708932](简析Apache如何解析HTTP请求/1575646708932.png)

## 结语

代码最后看下来，在HTTP解析这块，我们需要关注的要点不是很繁多，而C代码只以业务功能的视角来浏览的话，不难。

不过没有看到Apache解析multipart/form-data的代码，说明这种内容格式是web语言处理的。