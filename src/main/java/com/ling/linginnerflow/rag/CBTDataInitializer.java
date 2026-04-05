package com.ling.linginnerflow.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CBT知识库初始化器
 * 项目启动时自动把CBT内容同时上传到：
 * 1. Pinecone（向量检索）
 * 2. Elasticsearch（关键词检索）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CBTDataInitializer implements ApplicationRunner {

    private final CBTKnowledgeService cbtKnowledgeService;
    private final CBTDocumentRepository cbtDocumentRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始初始化CBT知识库...");

        // 检查ES里是否已有数据，避免重复上传
        long esCount = cbtDocumentRepository.count();
        if (esCount > 0) {
            log.info("ES知识库已有{}条数据，跳过初始化", esCount);
            return;
        }

        Map<String, String> cbtData = buildCBTData();

        int pineconeSuccess = 0;
        int esSuccess = 0;

        for (Map.Entry<String, String> entry : cbtData.entrySet()) {
            String id = entry.getKey();
            String text = entry.getValue();

            // 上传到Pinecone（向量检索）
            try {
                cbtKnowledgeService.upsertCBTContent(id, text);
                pineconeSuccess++;
                Thread.sleep(500);
            } catch (Exception e) {
                log.error("Pinecone上传失败: id={}", id);
            }

            // 写入ES（关键词检索）
            try {
                CBTDocument doc = new CBTDocument();
                doc.setId(id);
                doc.setContent(text);
                // 从ID提取类别，如CBT-001 → 认知扭曲识别
                doc.setCategory(extractCategory(id));
                cbtDocumentRepository.save(doc);
                esSuccess++;
            } catch (Exception e) {
                log.error("ES写入失败: id={}", id);
            }
        }

        log.info("CBT知识库初始化完成，Pinecone: {}/{}，ES: {}/{}",
                pineconeSuccess, cbtData.size(),
                esSuccess, cbtData.size());
    }

    /**
     * 根据ID判断类别
     */
    private String extractCategory(String id) {
        int num = Integer.parseInt(id.replace("CBT-", ""));
        if (num <= 8) return "认知扭曲识别";
        if (num <= 14) return "负面思维挑战";
        if (num <= 20) return "情绪调节技巧";
        if (num <= 23) return "行为激活";
        if (num <= 26) return "焦虑管理";
        return "自我关怀与核心信念";
    }

    private Map<String, String> buildCBTData() {
        Map<String, String> data = new LinkedHashMap<>();

        data.put("CBT-001",
                "【全或无思维】全或无思维又称黑白思维，表现为用极端类别来看待事物。" +
                        "如果你表现得不够完美，就觉得自己彻底失败了。识别方法是观察内心对话中" +
                        "是否高频出现总是、从不、完全等词汇。例如，面试没过就认为我永远找不到工作。" +
                        "挑战这种扭曲的方法是寻找灰色地带，意识到成功和失败之间存在连续的谱系。");

        data.put("CBT-002",
                "【灾难化思维】灾难化是指在事情还没发生时，就预见最糟糕的结果。" +
                        "识别它需要注意那些以万一……怎么办开头的念头。" +
                        "应对策略是进行去灾难化练习，客观评估最坏情况发生的真实概率，" +
                        "并思考如果真的发生了，你拥有的应对资源是什么。");

        data.put("CBT-003",
                "【读心术】读心术是指在没有证据的情况下，断定别人在以负面方式看待你。" +
                        "识别关键在于反问自己：我有客观证据证明他在想什么吗？" +
                        "要意识到他人的行为可能源于他们自己的情绪，而非针对你。");

        data.put("CBT-004",
                "【情绪化推理】情绪化推理是指你认为自己的负面情绪反映了事实真相。" +
                        "逻辑是：我感觉很糟，所以事情一定很糟。" +
                        "识别需要练习将感觉与事实分离。情绪是暂时的神经反应，不代表现实的客观状况。");

        data.put("CBT-005",
                "【过滤镜效应】你只关注一件事中的负面细节，屏蔽所有正面信息。" +
                        "就像一滴墨水染黑了整杯水。识别它需要刻意寻找被你忽略的积极信息，" +
                        "强制扩大注意力的范围，意识到这是认知偏差而非事实的全部。");

        data.put("CBT-006",
                "【应该陈述】用应该、必须或一定要来要求自己或他人。" +
                        "针对自己时导致内疚感；针对他人时产生愤怒。" +
                        "识别它需要将应该替换为如果能……会更好，" +
                        "这种弹性表达能减少心理上的自我批判压力。");

        data.put("CBT-007",
                "【贴标签】用一个负面定论完全定义自己或他人。" +
                        "例如犯了错就说我是个失败者，而不是我犯了一个错。" +
                        "需要意识到人是动态复杂的，一个行为不能代表一个人的全部价值。");

        data.put("CBT-008",
                "【个人化】认为自己对并非由你完全控制的负面事件负有主要责任。" +
                        "需要理清责任边界，意识到他人的情绪受多种因素影响。" +
                        "学会区分受影响与应负责，能有效缓解无端的自责。");

        data.put("CBT-009",
                "【证据检验】当负面自动思维出现时，通过苏格拉底式提问进行证据检验。" +
                        "问自己：支持这个想法的客观事实是什么？反对这个想法的证据又有哪些？" +
                        "通过客观评估，你会发现负面念头往往基于情感而非事实。");

        data.put("CBT-010",
                "【替代解释】面对令人沮丧的事件，强迫自己写出至少三个其他的可能性。" +
                        "例如朋友没回信息，除了他不在乎我，替代解释可以是：他很忙、手机没电、" +
                        "被别的事打断了。这种练习能增加认知灵活性，减少情绪波动。");

        data.put("CBT-011",
                "【下沉箭头技术】用于挖掘自动思维背后的深层恐惧。" +
                        "问自己：如果这个念头是真的，那意味着什么？通过连续追问找到最底层的核心信念。" +
                        "识别到这一层后，能针对性地安慰那个受惊的内在自我。");

        data.put("CBT-012",
                "【认知重组：平衡思维】平衡思维不是盲目正能量，而是追求客观。" +
                        "目标是生成一个更温和全面的陈述。例如将我一事无成重组为：" +
                        "虽然我今天遇到了挑战，但我也完成了一些事，并在努力寻求改进。");

        data.put("CBT-013",
                "【双重标准技术】当你对自己极其苛刻时，问自己：" +
                        "如果我的好朋友遇到同样的情况，我会怎么对他说？" +
                        "通过视角切换，用安慰朋友的语气来安慰自己，" +
                        "这种自我慈悲的练习能迅速降低自我批判带来的内疚感。");

        data.put("CBT-014",
                "【行为实验】挑战负面思维最强有力的方法是进行小型实验。" +
                        "通过观察现实结果，用现实证据直接击碎扭曲的预测。" +
                        "鼓励用户进行低风险尝试，从而重塑对世界的信任。");

        data.put("CBT-015",
                "【5-4-3-2-1着陆技术】当极度焦虑时，通过感官寻找：" +
                        "5个能看到的物体，4个能触摸的感觉，3个能听到的声音，" +
                        "2个能闻到的味道，1个能尝到的味道。" +
                        "这能中断焦虑风暴，帮你回到当下。");

        data.put("CBT-016",
                "【情绪命名】仅仅是把情绪用文字命名出来，就能显著降低大脑杏仁核的活跃度。" +
                        "不要抗拒情绪，而是像观察路人一样观察它，并给它起个名字。" +
                        "一旦情绪被命名，它就从无名的怪兽变成了可观察的对象。");

        data.put("CBT-017",
                "【正念呼吸】正念呼吸不是要消除思绪，而是观察思绪。" +
                        "将注意力集中在呼吸进入和离开身体的感觉上。" +
                        "当注意力走神时，温和地把它拉回来，重点在于不评判。");

        data.put("CBT-018",
                "【身体扫描】压力往往以物理方式储存在身体里。" +
                        "身体扫描是从脚趾到头顶，逐一关注每个部位的感受。" +
                        "当意识到某个部位紧绷时，有意识地呼吸并放松它，加强身心连接。");

        data.put("CBT-019",
                "【渐进式肌肉放松】通过先绷紧肌肉再迅速放松，让身体感知深度松弛。" +
                        "例如用力握紧拳头5秒，然后突然松开，感受放松感。" +
                        "对缓解焦虑引起的失眠和躯体化症状非常有效。");

        data.put("CBT-020",
                "【情绪冲浪】将强烈的情绪比作海浪，海浪会升高、达到顶峰、然后自然退去。" +
                        "你不需要压制浪花，只需观察它。" +
                        "提醒自己：情绪是暂时的，只要不立刻根据冲动行事，海浪终会平息。");

        data.put("CBT-021",
                "【愉悦活动清单】当陷入低动力时，建立微小易行的愉悦清单：" +
                        "如喝一杯好咖啡、摸摸猫、看5分钟治愈视频。" +
                        "不要等有了心情再去做，而是先行动，让行为带动情绪。");

        data.put("CBT-022",
                "【小步骤原则】将任务拆解到不可失败的程度。" +
                        "目标不是打扫房间，而是捡起地上的一只袜子。" +
                        "通过达成微小目标，大脑会释放多巴胺，逐渐修复受损的动力系统。");

        data.put("CBT-023",
                "【阻碍识别与排除】行为激活失败往往因为潜意识中的阻碍。" +
                        "识别到我太累了或做了也没用这类念头后，" +
                        "用实验心态挑战它：我可以试着只做2分钟，看看会发生什么。");

        data.put("CBT-024",
                "【担忧时间技术】每天设定固定的15分钟作为担忧时间。" +
                        "其他时间出现烦恼时，告诉自己：现在不是担忧时间，" +
                        "并在脑中把它放入虚拟的盒子。通过这种方式重新夺回对大脑的控制权。");

        data.put("CBT-025",
                "【打破逃避循环】焦虑的维持往往靠逃避。" +
                        "你越逃避让你害怕的事，大脑就越认为那是致命威胁。" +
                        "打破循环的方法是有限暴露，在安全可控的情况下慢慢接触让你不安的事物。");

        data.put("CBT-026",
                "【接受与承诺ACT】痛苦是生活的一部分，试图消除所有焦虑反而会产生更多焦虑。" +
                        "核心是接纳：允许焦虑存在，但不让它开车。" +
                        "你可以带着焦虑去做事。当你不去抗拒焦虑时，它反而会变得不那么具有侵略性。");

        data.put("CBT-027",
                "【自我关怀vs自我批评】自我批评是内在的暴君，认为严厉能驱动进步；" +
                        "自我关怀是内在的慈母，认为支持能带来康复。" +
                        "当你失败时，试着像对待受了伤的孩子那样对自己说话。");

        data.put("CBT-028",
                "【识别底层的我不够好】核心信念是你对世界和自我的根本看法，通常在童年形成。" +
                        "常见的负面信念包括我是不可爱的、我是无能的。" +
                        "一旦意识到这只是习得的偏见而非真理，你就开始了重塑自我的过程。");

        data.put("CBT-029",
                "【建立新的正向信念】重构核心信念不是简单地喊口号，而是收集证据。" +
                        "建立成功日志，记录每一个微小的正面时刻。" +
                        "当积累了足够多的证据证明我也被爱着或我有应对困难的能力时，" +
                        "旧的负面信念就会逐渐瓦解。");

        data.put("CBT-030",
                "【核心信念重构：平衡陈述】将极端的核心信念转化为平衡的信念。" +
                        "例如将我一无是处重构为：我是一个有优点也有缺点的人，我正在学习和成长中。" +
                        "当检测到深层的自我否定时，帮助用户从绝对化的自我批判中解脱出来。");

        return data;
    }
}