---
date: 2023/8/16 00:00:00
---

# A Guided Tour of Cilium Service Mesh - Liz Rice, Isovalent

https://www.youtube.com/watch?v=e10kDBEsZw4

cilium发展到如今，大部分能力其实更多地支持了服务网格功能。

![image-20230816102616894](cilium服务网格之旅(译)/7.png)

cilium的service mesh功能更优秀..是时候和sidecar模型说再见了（istio）

![image-20230815170932747](cilium服务网格之旅(译)/11.png)

cilium作为k8s的网络插件，具有十分高效的性能和广泛的兼容扩展，你可以单独使用它，但更多的用户会选择在k8s环境中应用cilium。

通过cilium我们可以观测workload之间或与外网的网络活动情况，cilium也实现了网络加密、流量负载功能，可以用于替换kube代理。

![image-20230815170948015](cilium服务网格之旅(译)/17.png)

我们还提供了 `hubble` 组件，它将网络流量可视化，通过它我们十分简便地获取到每个流量包的流量日志细节，也能将 service map 可视化方便我们理清 k8s service情况，还能将 metrics 导入到 prometheus/grafana 中.（备注：实际上cilium是观察当前的通信流量来总结得出网络情况，并不具备根据目前配置情况自动总结的能力，这也是让笔者稍微失望的一点）

我们也将 L3 L4 L7 各层流量都可视化了，而L7正是应用流量，诸如HTTP RPC。

![image-20230815171614038](cilium服务网格之旅(译)/25.png)

在我们具体讲述cilium的能力细节之前，我们来稍微回顾 服务网格 的历史发展。

不同语言实现的不同服务，彼此之间的交流依赖于HTTP、grpc 等协议，协议下层也可能使用TLS封装 确保安全，到今天为止，我们要实现这些网络功能需要import相应的库，而如果你要实现 service mesh中的 服务发现、连接断开重试、均衡、限速等功能，你需要在不同的语言应用代码中做大量的工作

![image-20230815172251311](cilium服务网格之旅(译)/31.png)

因此，在上一阶段后，我们转到使用 side car 模型。

k8s允许我们将多个容器划为一组 pods ，pods 共享 network namespace，这使得我们可以在pod中使用 sidecar 代理，sidecar提供了service mesh功能。例如你不用在pod中设置tls，只需将其交给sidecar代理，其他可涵盖的功能还包括 请求重试、负载均衡 等。这个模式下，开发者的任务变得轻松很多，开发者视角下这个机制十分高效。

sidecar proxy可以是三方组件，这个模型下需要为每个pod维护一个单独的sidecar proxy，比较流行之一的是 envoy。

备注：istio实现了sidecar模型，对应的sidecar proxy就是envy，参考 https://jimmysong.io/blog/envoy-sidecar-injection-in-istio-service-mesh-deep-dive/

![image-20230815174443168](cilium服务网格之旅(译)/41.png)

我们从这个模型中也引声出这么一个问题：一开始在应用中的 service mesh 功能机制移动到了 pod 中，那么我们能否再将这个功能移动到内核中呢？

为何我们要这样想？一个例子就是，网络协议栈的TCP/IP这些协议，曾经也是需要开发者自己在user space下的应用中编写相应的协议功能。而现在我们都习惯了让内核来处理这些网络协议栈的东西。

如果我们认为k8s是一个分布式的操作系统，服务网格 与 网络协议栈 又如果真的同等重要，那么我们是否可以将 服务网格 的功能机制放到内核中来？这个问题的答案是，不是所有的功能都可以，但基本上都已经做到了或可以做到。

![image-20230815220220442](cilium服务网格之旅(译)/51.png)

L7 是内核目前没有处理到的协议栈。我们认为内核最终会有能力处理，但这天到目前为止都还没到来。

cilium之前已经具备了 L3 L4 协议栈的处理能力，诸如安全、流量负载、日志功能。而到目前为止（我宣布），cilium已经能处理L7流量了。

![image-20230815221729156](cilium服务网格之旅(译)/57.png)

备注：作者现场演示了如何通过cilium配置七层 HTTP 协议的特定拦截策略，所用案例参考以下链接即可，这里不过多说明

  https://docs.cilium.io/en/stable/gettingstarted/demo/#apply-and-test-http-aware-l7-policy

![image-20230815222715452](cilium服务网格之旅(译)/63.png)

在相应的流量测试过程中，我们可以通过hubble的可视化页面看到流量的数据详情及各项指标情况。

![image-20230815223204273](cilium服务网格之旅(译)/67.png)

我们实际上通过envoy proxy来实现L7可感知的这一功能。cilium目前使用eBPF在内核动态编程实现管控网络终端与连接，而cilium需要查看L7流量或中断其中的连接，都会使用envoy来实现。

![image-20230815223349737](cilium服务网格之旅(译)/71.png)

我们对用户进行了调查，得出了在service mesh中用户感兴趣/关注 的功能特性：

![image-20230815224409054](cilium服务网格之旅(译)/75.png)

这一系列的功能实际上在之前 cilium 就已经支持了，80%的所谓的service mesh软件也能支持这些功能。所以我们不得不做得更多来表现不同。

![image-20230815224652213](cilium服务网格之旅(译)/79.png)

cilium不是单纯地使用envoy作为proxy，我们让 envoy 变得可编程（动态修改），通过ebpf动态更新流量策略来应用你所相要的可视化、流量规则。

![image-20230816090508279](cilium服务网格之旅(译)/83.png)

cilium支持ingress（流量入口）策略，下面演示了如何查看ingress策略，对相应的策略进行网络测试。

![image-20230816091253642](cilium服务网格之旅(译)/87.png)

![image-20230816093930362](cilium服务网格之旅(译)/89.png)

![image-20230816094152912](cilium服务网格之旅(译)/91.png)

![image-20230816094207489](cilium服务网格之旅(译)/93.png)

![image-20230816094232773](cilium服务网格之旅(译)/95.png)

![image-20230816094340541](cilium服务网格之旅(译)/97.png)

下面是一系列beta版本的测试用户的评价：

![image-20230816094933910](cilium服务网格之旅(译)/101.png)

![image-20230816095004550](cilium服务网格之旅(译)/103.png)

![image-20230816095041591](cilium服务网格之旅(译)/105.png)

sidecar模型会为每个pod维护单独的代理，并维护单独的路由实例，这会占用大量的资源，而cilium模式下的 service mesh只会在一台node上维护一个。

![image-20230816095120448](cilium服务网格之旅(译)/109.png)

sidecar模型下的app流量出网，在网络协议栈可以抽象表现为如下过程，可以看到这经过了许多过程（经过3次网卡）：

![image-20230816095246959](cilium服务网格之旅(译)/113.png)

cilium下的app流量，大部分流量不会命中L7的策略，所以通常到L3/L4就会被放行（备注：说明cilium L7策略会在L3/4做前置过滤）：

![image-20230816095441831](cilium服务网格之旅(译)/117.png)

service mesh场景下，需要应用策略处理的流量则会经过 envoy proxy

![image-20230816095859015](cilium服务网格之旅(译)/121.png)

这样一来，我们只需要在每个node维护一个proxy即可，再加上eBPF的能力，cilium在带宽与延迟这块都对传统模型有较大的提升。

https:/isovalent com/bloa/post/2022-05-03-servicemesh-security

![image-20230816100659221](cilium服务网格之旅(译)/127.png)

![image-20230816101030454](cilium服务网格之旅(译)/129.png)

相应的容器启动时间也降低很多：

![image-20230816101054024](cilium服务网格之旅(译)/133.png)

控制面板这块我们已经兼容了很多产品

![image-20230816101141188](cilium服务网格之旅(译)/137.png)

关于Mutual-TLS（双向认证TLS），我们计划将认证模块与加密模块分割开，起因是因为我们已经拥有了十分高效的网络层的加密能力，如ipsec、wireguard。加密模块能在内核中。但我们需要将 证书机制 注入到内核中来实现 TLS这块的加密。(备注：cilium支持TLS的可视化，与防火墙的TLS中间件人一样的原理，使用这个功能则需要客户端信任cilium的CA，另外就是注意cilium信任哪些CA)

这块功能将在1.13版本中发布。

https://isovalent.com/blog/post/2022-05-03-servicemesh-security/

![image-20230816101304933](cilium服务网格之旅(译)/145.png)

![image-20230816101735273](cilium服务网格之旅(译)/147.png)

![image-20230816102259364](cilium服务网格之旅(译)/149.png)

![image-20230816102514486](cilium服务网格之旅(译)/151.png)