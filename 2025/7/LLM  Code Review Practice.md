# LLM  Code Review Practice

## 1. 前言

&emsp;&emsp;相信很多人都对LLM的应用场景这一课题进行过思考，而我想来最直接有效的就是在代码审计这一方面上，今年也在这块进行了一些尝试。虽然之前在微信上看到@xsser的文章中提到不认可这一应用场景，原文的话语笔者记不清了，但大致意思是“LLM白盒是没有价值的场景，以后LLM编写的代码就直接不存在漏洞了"。2个点，他们是做黑盒渗透测试扫描器的，不认可白盒应用场景，就奇怪（白盒“差不多”是黑盒的超集），另外一点就不非口唇了，无所谓“不存在漏洞”之说。

&emsp;&emsp;3月份的字节线上沙龙上，字节无恒实验室分享了他们在白盒上的一些实践，分享中说到白盒审计的漏洞主要是在水平越权与鉴权代码缺陷检测。从他们分享的内容来看，LLM在“代码片段”上的处理能力是比较强的，在代码上下文的关联能力是比较差的；腾讯白盒团队也在他们文章中提到结合SAST工具会让LLM CodeReview更高效。字节给出的数据是误报率50%，由于涉及到越权漏洞，实际上这个准确率在笔者看来是比较不错的，不过分享中没有给出哪怕是实验性测试的漏报率。

&emsp;&emsp;本文阐述一下笔者目前的一些实践情况，希望能给大家带来一些参考价值。

## 2. 实践思路

&emsp;&emsp;任务目标之一也是检测水平越权漏洞，落地上依靠提示词+工程化来实现实现代码审计这一目标。由于笔者是需要在内网去具体实践的，起初就考虑到内网模型能力差的情况，因此相关提示词是在能力差的模型上开始积累编写的。至于如何编写提示词，建议参考Cline的相关提示词（比如多了分割线，提示词效果会更好点，让人惊奇），在事情开头有一个优秀的对象可以参考模仿可谓是事半功倍。

- SAST构建函数调用图：负责提取项目入口点Source信息，并从Source开始获取函数的调用路径，默认忽略掉非项目源码中的函数调用。此外，提取出的函数调用图各个函数的代码段也会被一同提取，同时各方标记具体的行号方便人工review。
- 串联多个“角色"输出结果
  - sink agent：审计函数调用图，找到目标sink方法；
  - vuln agent：正向顺序审计代码，source->sink，判断是否存在越权问题
  - business agent：逆向顺序审计代码，sink->source，判断业务场景以及是否存在越权危害
  - 复查：每个agent审计结束后都会要求其进行复查，且进行格式化输出（起初system prompt不会让LLM格式化结论，在LLM完成任务后再让其格式化输出）

## 3. 构建函数调用图

&emsp;&emsp;LLM真的关联代码能力是比较弱的吗？给LLM提供了各种有效的工具的情况下，经过一番测试，发现发现大家说的确实没错...一方面LLM对方法的关联成功率很低，测试为不到50%；另外一方面，即便能成功关联，一个也需要花费2-3分钟时间，相比于SAST的几秒时间来说效率太低。

&emsp;&emsp;为了快速进行实践，这里使用了`soot-up`对项目编译后的JAR/CLASS进行解析处理。由于codeql涉及到license问题，因此起初是想着用类似于codeql的joern，但joern实在是太太太难用了，真的是费事又难以工程化使用的工具。

- source的提取：通过编写代码适配相关框架即可，考虑到有些微服务入口参数是网关认证后的参数，因此有些情况下source的数据还会带到system prompt中用于标记哪些入口数据是可信的。当然，后面为了方便可以source做成配置文件形式。
- 函数调用图：[soot-up文档](https://soot-oss.github.io/SootUp/latest/callgraphs/) 中给了以下示例，可以直接获取代码中的method call具体的类方法，之后以递归循环的方式就可以得到函数调用图。在具体实现时可以进行一些过滤避免无用的节点，如将POJO的set/get调用过滤掉，并限制整个函数调用图的深度和节点数量。整个函数调用图实际为Tree结构，扩展时应优先广度。

```java
CallGraphAlgorithm cha = new ClassHierarchyAnalysisAlgorithm(view);

CallGraph cg = cha.initialize(Collections.singletonList(entryMethodSignature));

cg.callsFrom(entryMethodSignature).stream()
    .forEach(tgt -> System.out.println(entryMethodSignature + " may call " + tgt);
```

`call_graph`即函数调用图格式参考如下，并作相关说明：

- depth：当前方法在整个方法调用中的深度；
- uid：方法的唯一ID，方便LLM后续直接使用该uid获取方法的代码；
- 方法的格式为 `pkgname.ClassName(PatamerType...) returnType` ，标记返回类型会更好点，同时注意无论是JDK类还是项目类都需要使用全称类名，这样效果会更好；
- 接口方法跳转实现：第四行中，同一行存在两个方法节点，前者为接口方法，后者为实现方法。在实践中发现这样处理是最好的，让LLM注意到这种特色情况，得以忽略接口方法，同时了解到后者是实现方法，不然大模型会晕头转向的。

```python
depth: 0
-> uid: 1, method: com.test.controller.CampAttendController.insertCampAttend(java.util.Map) ,return_type: java.util.Map
  depth: 1
  -> uid: null, method: com.test.portal.marketing.service.CampAttendService.insertCampAttend(java.util.Map) ,return_type: java.util.Map-> uid: 2, method: com.test.portal.marketing.service.impl.CampAttendServiceImpl.insertCampAttend(java.util.Map) ,return_type: java.util.Map
    depth: 2
    -> uid: null, method: com.test.portal.redis.dao.JedisDao.hget(java.lang.String,java.lang.String) ,return_type: java.lang.String-> uid: 3, method: com.test.portal.redis.dao.impl.JedisSentinelDaoImpl.hget(java.lang.String,java.lang.String) ,return_type: java.lang.String
    -> uid: null, method: com.test.portal.marketing.pojo.CampAttend() ,return_type: void
    -> uid: 4, method: static com.test.portal.commons.utils.IDUtils.getUUID() ,return_type: java.lang.String
    -> uid: 5, method: com.test.portal.marketing.pojo.CampAttend.setId(java.lang.String) ,return_type: void
    -> uid: 6, method: com.test.portal.marketing.pojo.CampAttend.setCampCode(java.lang.String) ,return_type: void
    -> uid: 7, method: com.test.portal.marketing.pojo.CampAttend.setMemberId(java.lang.String) ,return_type: void
    -> uid: 8, method: com.test.portal.marketing.pojo.CampAttend.setSource(java.lang.String) ,return_type: void
    -> uid: null, method: com.test.portal.marketing.dao.CampAttendDao.insertCampAttend(com.test.portal.marketing.pojo.CampAttend) ,return_type: int-> uid: 9, method: com.test.portal.marketing.dao.impl.CampAttendDaoImpl.insertCampAttend(com.test.portal.marketing.pojo.CampAttend) ,return_type: int

```



入口方法最终获得的函数调用图结构参考如下，该数据作为审计任务的初步输入数据：

- file：代码的绝对路径，在输入给LLM时会转为项目文件下的相对路径
- code：方法所在类的类申明及注解注释、方法的代码及注解注释会被拼接作为代码，由于LLM的优势是能语义理解、表意理解，因此尽可能收集相关注释信息。

```json
{
  "uid": "1",
  "file": "/prefix/project/path/to/TestController.java",
  "code": "@Controller
public class TestController{
19: @RequestMapping(\"/anon/h5/insertCampAttend\")
20:     @ResponseBody
21:     public Map<String,Object>insertCampAttend(@RequestBody Map<String,String> params){
22:         return campAttendService.insertCampAttend(params);
23:     }",
  "pkg_name": "com.xxx.controller",
  "class_name": "TestController",
  "method_name": "insertCampAttend",
  "method_type": "normal",
  "return_type": "java.util.Map",
  "args_type": "(Map)",
  "return_type_file": null,
  "api_autowried_args": null,
  "call_graph": 
}
```

## 4. role（agent）

&emsp;&emsp;提示词的构造是Agent实现的和核心，提示词的编写上可以有以下这些部分： 声明角色与任务目标、工具列表与调用描述、知识描述与下定义、案例说明、任务规则遵循事项、任务详细步骤。

工具部分，笔者只为LLM提供了三个工具：

```
<get_method_code><uid>请填写uid<uid></get_method_code>

<get_call_graph><uid>请填写uid<uid></get_call_graph>

<completion><result></result>/completion>

```

下面具体讲一下任务prompt中的关键实现点。

### 4.1. sink

&emsp;&emsp;sink role负责找到数据操作的污点，由于需要适配微服务应用，因此所谓的污点还包括远程调用的方式；这里让LLM以“sink链”的方式输出结果，可以避免llm错误输出非末端的sink点，这里所谓的“sink链”实际就是入口方法到sink的调用过程。任务输出的sink chain给到后续其他role使用时只将末端方法看作sink。

>污点的定义：
>
>- 数据操作（QUERY/INSERT/UPDAE/DEETE）行为，包括当不限于DAO层、SERVICE层、直接调用JDBC执行SQL，直接或间接的调用；
>- 调用第三方接口服务进行数据操作，包括RPC调用、微服务调用、HTTP调用
>
>污点方法关键字：query insert update delete
>
>调用污点这也是污点：如果该方法代码中存在sink，则将该方法也记录为sink



>污点记录格式
>
>当你发现一个sink时，将其加入sinks列表中，如果sink中进一步调用了sink，请通过“->”连接两个sink，并避免递归循环问题。
>
>```
>sinks:
>- sink: xx(..) -> xx(..)
>```
>
>



### 4.2. vuln

可信数据与不可信数据的提示词：

>
>
>- 什么是用户：根据应用的业务逻辑的不同，用户可以存在不同的实体名称，请根据应用的业务逻辑来判断。用户指“应用的用户”，因此可以扩展为商城用户、商户等情况，根据业务场景而变化。
>- 唯一标识字段：包括但不限于ID字段、用户ID、手机号、用户名称、邮箱、会话ID等。唯一标识字段是关于用户表或业务表中的unique key。
>
>- 一共xx类可靠的数据PURE：
> 1. 无参函数方法的返回（从缓存获取数据），包括获取用户ID、获取缓存的用户SESSION数据、获取BeanFactory缓存的数据；
>
> 2. 来自无越权的数据查询的结果的“唯一标识字段”数据；
>
> 3. JWT Token（用户Token及Token相关数据），Token可以从用户的输入获取（参数、Header），通过token获取的数据是可靠的；
>
> 4. 参考“微服务参数”，这是用户中心经过校验的数据，请将其看做可靠的数据；
>
> 5. 通过redis数据库或Map获取到的数据（map/redis get） ...
>
>    
>- 不可信的数据SOURCE：来自用户的输入且不是第三类可靠的数据PURE。





漏洞检测的关键步骤的prompt如下，另作说明:

- 以明确步骤的方式提升任务稳定：agent的提示词如果没有“1 2 3” 步骤，幻觉会比较严重且输出结果不稳定，在有顺序步骤之后，LLM的会遵守相应的任务步骤因此结果更稳定；
- 以“有限状态机”的方式来获取任务结果：实际上LLM在小片段的逻辑中表现好，当你给LLM的任务越简单越机械化LLM就越可靠。因此，当你指定的LLM的任务比较复杂时，则应尽量避免让LLM来判断结果；虽然LLM大多数情况下会给出正确的推理过程，但是很可能不会给出正确的推理结果（笔者理解这是因为这里让LLM做的事情比较专业深入，和LLM的”普通逻辑“不太符合）。为了让最终的任务结论可靠，这里设计了一套 status_list 来配合任务步骤藉以得到正确的结论。
- 拆分逻辑上的子集避免“注意力分配受限”：在水平越权检查中，query与update都需要对其入参做检查，这一步LLM会表现得比较好，但是query还需要额外检查数据是否真的返回给了用户。前一步的检测LLM表现良好，但是后者LLM就表现得很糟糕，将后者拆分为另外一个新任务时可以发现LLM对后者得检测又变得良好，这可能是前后者的任务逻辑方向大相径庭，LLM的注意力有限因此表现比较差。这也是编写任务prompt中比较麻烦的点，你需要理解任务逻辑，拆分其中的一些大方向上的任务逻辑。下面的检测步骤去掉了后一步的判断，将后一步放到了新任务中去处理。

>
>
>针对每个sink，请严格按照下面的步骤进行适配越权漏洞检测
>
>1. 本次任务的代码阅读为”正向顺序阅读“，请结合”函数调用图“一直获取入口到sink点的所有代码；
>2. 为sink点建立 status_list，目前为空；
>3. 检查入口方法到sink点之前的代码，判断是否存在”流程控制“校验权限，如果存在就向 status_list 添加 white；
>4. 审计入口方法到sink点之前的代码，进行数据标记工作
>5. 判断该sink点的数据操作类型为 数据查询（QUERY）或 数据更新（INSERT/UPDATE/DELETE），status_list 添加 ready；
>6. 检查sink的输入是否存在唯一标识字段（对象的相关字段一并检查），如果不存在则status_list添加black；
>7. 检查sink的输入的唯一标识字段数量：请判断都有哪些输入是唯一标识字段，且数量多少；
>8. 遍历sink的入参进行如下操作：
>   - 当 status_list 包含white时，将sink输出的数据标记为可靠的数据。
>   - 当 status_list 不包含white时，将sink输出的数据标记为不可信的数据。
>9. 向 status_list 添加 finish



>
>
>数据标记工作
>
>在你开始审计每一个代码方法去，你需要获取当前数据标记情况；在你退出当前代码方法或是跳转到另外一个代码方法前，你需要对数据进行标记：
>
>1. 获取本方法入参数据的标记情况：记录并标记可信数据、不可信数据；
>2. 代码审计时，标记可靠的数据与不可信的数据，完成当前方法的审计后，记录当前各个变了、对象字段的数据标记情况。

### 4.3. business

&emsp;&emsp;business的提示词这里不作说明，business role在普通的业务场景下表现还好，但是涉及到特定业务且代码审计人员自己去看也不一定能理清逻辑的情况下business role自然也无法有所表现。

&emsp;&emsp;business这块还在改进之中，笔者认为最好的思路还是让LLM把最终的数据操作行为操作的表关联起来（还是需要人工标记数据库表及字段的数据敏感性）。这个思路在非微服务应用中相较容易实现，最终的SQL执行语句是比较容易拿到的；在微服务应用中，只能通过rpc方法返回的DTO Class字段去关联表列，这个相对容易出错，尤其是开发在字段命名混乱的情况下更易出问题。但无论如何来说，涉及到业务漏洞这块，LLM始终还是需要人工标记的数据作为输入，只是或许存在一个好的方案让这个输入涉及的人工操作更少更便捷。



这里提供一个项目的测试数据作参考，使用的旧漏洞版本作为测试目标。为了衡量最终结果的情况，需要通过 真阳越权、真阳危害去评估，从结果上来上，LLM的越权行为判断准确率比较高，而这是因为这个检测判断逻辑可以与业务逻辑无光，是一个较为通用的逻辑；但在漏洞具体是否有危害（被操作的字段是否需要做权限限制），这种涉及到业务场景逻辑的，由于人都不一定能直接判断，LLM也无法做到。因此，这里的漏洞准确率也是没有参考价值的，另外项目可能很低。

|                | qwen3-235 |
| ------------------------------- | --------- |
| 漏报率                                  | 6%        |
| 越权行为判断准确率（真阳越权/报告漏洞） | 88%       |
| 漏洞准确率（真阳危害/报告漏洞）         | 55%       |



## 5. 总结

&emsp;&emsp;目前实践来看，LLM对“逻辑紧凑”的任务表现得比较良好，通过工程化我们可以让LLM去覆盖一些人工的审计工作，但这也需要科学地去思考项目可行性，当人工都无法直接判断的逻辑LLM也是无法给出期望的正确答案，最好的实践也无非是人工是怎么做的，LLM也需要怎么做，人工有哪些知识输入，LLM也需要相应的格式化输入。
