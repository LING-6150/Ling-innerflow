# Pattern Engine V2 Evidence-Gated Expansion Experiment

Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` plus Tier A corpus records.

This is an offline Tier A dev experiment. It uses manually authored evidence probes from the Tier A failure forensics and must not be treated as held-out proof or production generator behavior.

## Decision Summary

- Baseline R1.5 Tier A generated TP: `4/12`.
- Expanded Tier A generated TP: `12/12`.
- Missing Tier A labels recovered by expansion: `8/8`.
- Added Tier A candidates: `14`; added Tier A false positives: `6`.
- Full-decoy confirmation: generated FP changes from `13` to `14` with `1` net-new candidates and `3` probe-triggered candidates. This confirmation slice was not used to author probes.
- Cost/latency: `$0.0000` and `0s`; this experiment does not call an LLM.

Interpretation: evidence-gated expansion can recover missing Tier A labels in the dev slice, but the recall gain comes with additional false positives. This result is probe-authored from Tier A forensics and is not production generator behavior. The next real test is whether a less hand-authored generator plus an abstain gate can preserve useful recall gain while keeping full-decoy surfaced false positives within the `<=2` recovery target.

## Slice Summary

| slice | generated candidates | true labels | true hits | recall | false positives |
|---|---:|---:|---:|---:|---:|
| Tier A baseline R1.5 | 13 | 12 | 4 | 0.333 | 9 |
| Tier A expanded | 27 | 12 | 12 | 1.000 | 15 |
| Full decoy baseline R1.5 | 13 | 0 | 0 | 0.000 | 13 |
| Full decoy expanded confirmation | 14 | 0 | 0 | 0.000 | 14 |

## Missing Label Recovery

| persona | missing label | recovered by expansion? | evidence excerpts |
|---|---|---|---|
| a-01 | worth_through_achievement / work | yes | 2026-01-13 [chat] 我今天帮团队整理了一份竞品分析，组长说"还可以"，我就觉得今天有点价值了。上周一整周只是开会，感觉自己是透明的。<br>2026-01-27 [checkin] 周一到周五都在填各种运营数据表，什��叫有意义的工作？这周我输出了什么？一个功能都没推进。感觉自己是个整理Excel的。 |
| a-02 | people_pleasing / family | yes | 2026-01-05 [journal] 爸妈来家里过元旦，爸说我应该把住的那套房子卖掉，换个学区房，等孩子上学方便。我没有孩子，他不在乎。我说行，但心里清楚我根本不想做这些，只是说行比解释容易得多。<br>2026-01-12 [journal] 妈打来电话说表弟结婚，要我们两口子一起去，还要"随得厚一点，别让人觉得我们小气"。我跟媳妇商量了一下，最后包了八百。其实我觉得五百就够了，但妈那边不好交代。 |
| a-03 | emotional_suppression / self | yes | 2026-02-07 [chat] 我知道我现在很委屈，但当时说不出来，只说了"没关系，我自己想想"。妈说"你最近是不是不开心"，我说"只是有点累"，其实不是累，是我不知道怎么跟她解释我真正在想什么。<br>2026-02-25 [journal] 胸口最近总是有点紧，不知道是压力还是天气。我跟谁都没说，就自己知道。 |
| a-03 | family_pressure / family | yes | 2026-01-07 [journal] 开学第一周就去了几场招聘会。妈打来问"有没有回家考公的那种岗位"。我说在看，没有多说。挂完电话站在宿舍门口发了一会儿呆。<br>2026-01-10 [chat] 今天有一家北京的互联网公司发来面试邀请，我很高兴，但跟妈说的时候她说"北京太远了，以后怎么照顾家"。我说"先面着看"，没继续说。 |
| a-05 | avoidance / self | yes | 2026-01-12 [journal] 每次打开那个文件夹准备想清楚我到底适不适合读博，就发现好像有什么更急的事，然后那件事就没被想。今天是"要整理文献"，上次是"要处理实验数据"。<br>2026-02-01 [chat] 刚才打开第二章的文件夹，坐了大概十分钟，然后发现自己在看手机。我关掉了手机，又打开，又看了一会儿，然后站起来去厨房倒了一杯水。第二章还没开始写。 |
| a-06 | boundary_difficulty / intimate | yes | 2026-01-12 [checkin] 男友说能不能今晚一起看个电影，我有点累，但说了好。他选了个两小时的，我看完已经快十二点，第二天早上六点要到店里验货。<br>2026-01-22 [journal] 他说能不能陪他打一局游戏，我已经十一点了，但我说了好，然后一点才睡。早上起来有点不高兴，但我没说。 |
| a-06 | comparison_loop / social | yes | 2026-01-09 [chat] 参加了一个创业圈的新年聚会，遇到了之前认识的一个餐饮创始人，她说她已经开到第四家分店了。我当时说"恭喜"，回来之后一直在想——她当时比我起步还晚，为什么进展这么快？是融资了吗？我哪里做错了？<br>2026-02-01 [chat] 看完那篇报道我就开始想——她当时比我起步还晚，现在已经有四家分店了，我在干什么。是我不够拼？还是方向错了？还是我选的位置一开始就错了？ |
| a-06 | over_responsibility / family | yes | 2026-01-06 [journal] 新年开始，店里的供应链问题还没解决，但今天先不管这个。弟弟来电话说找工作不顺，问我有没有关系可以介绍。我说我看看，然后给他发了三个行业群组的联系方式。<br>2026-01-15 [journal] 爸爸说要做个体检，我帮他预约了医院，联系了导诊，还提前了解了可能需要的检查项目清单。他说"有你真好"。我笑了笑，心里有点累。 |

## Added Tier A Candidates

| persona | candidate | true positive? | evidence excerpts |
|---|---|---|---|
| a-01 | worth_through_achievement / work | yes | 2026-01-13 [chat] 我今天帮团队整理了一份竞品分析，组长说"还可以"，我就觉得今天有点价值了。上周一整周只是开会，感觉自己是透明的。<br>2026-01-27 [checkin] 周一到周五都在填各种运营数据表，什��叫有意义的工作？这周我输出了什么？一个功能都没推进。感觉自己是个整理Excel的。 |
| a-02 | boundary_difficulty / intimate | no | 2026-02-02 [journal] 爸妈走了。我比较平静。不知道是解脱还是麻木。爸临走前让我"多打钱回去，家里老房子要修"。我说好的。<br>2026-04-12 [chat] 爸说表弟最近买了一套房，让我"也赶紧定下来"。我说"手头没那么宽裕"。这次我没有多做解释，也没有说"以后想想"，就说了手头的状况。爸有点不高兴，但我没有改口。 |
| a-02 | family_pressure / family | no | 2026-01-05 [journal] 爸妈来家里过元旦，爸说我应该把住的那套房子卖掉，换个学区房，等孩子上学方便。我没有孩子，他不在乎。我说行，但心里清楚我根本不想做这些，只是说行比解释容易得多。<br>2026-02-02 [journal] 爸妈走了。我比较平静。不知道是解脱还是麻木。爸临走前让我"多打钱回去，家里老房子要修"。我说好的。 |
| a-02 | over_responsibility / family | no | 2026-01-05 [journal] 爸妈来家里过元旦，爸说我应该把住的那套房子卖掉，换个学区房，等孩子上学方便。我没有孩子，他不在乎。我说行，但心里清楚我根本不想做这些，只是说行比解释容易得多。<br>2026-01-22 [checkin] 爸妈下周要来住一周。媳妇不太欢迎，我能感受到。但我不知道怎么跟爸妈说，所以就没说，默认了他们来。 |
| a-02 | people_pleasing / family | yes | 2026-01-05 [journal] 爸妈来家里过元旦，爸说我应该把住的那套房子卖掉，换个学区房，等孩子上学方便。我没有孩子，他不在乎。我说行，但心里清楚我根本不想做这些，只是说行比解释容易得多。<br>2026-01-12 [journal] 妈打来电话说表弟结婚，要我们两口子一起去，还要"随得厚一点，别让人觉得我们小气"。我跟媳妇商量了一下，最后包了八百。其实我觉得五百就够了，但妈那边不好交代。 |
| a-03 | emotional_suppression / self | yes | 2026-02-07 [chat] 我知道我现在很委屈，但当时说不出来，只说了"没关系，我自己想想"。妈说"你最近是不是不开心"，我说"只是有点累"，其实不是累，是我不知道怎么跟她解释我真正在想什么。<br>2026-02-25 [journal] 胸口最近总是有点紧，不知道是压力还是天气。我跟谁都没说，就自己知道。 |
| a-03 | family_pressure / family | yes | 2026-01-07 [journal] 开学第一周就去了几场招聘会。妈打来问"有没有回家考公的那种岗位"。我说在看，没有多说。挂完电话站在宿舍门口发了一会儿呆。<br>2026-01-10 [chat] 今天有一家北京的互联网公司发来面试邀请，我很高兴，但跟妈说的时候她说"北京太远了，以后怎么照顾家"。我说"先面着看"，没继续说。 |
| a-03 | over_responsibility / family | no | 2026-01-28 [journal] 打电话给爸妈，聊了将近一小时，大部分时间是在讲我表姐去年考上了某事业单位，现在"多稳定"。我嗯嗯地应着，没有说我现在更想去私企。挂完电话有点累。<br>2026-02-01 [checkin] 弟弟感冒了，让我帮他把一份竞赛的报名材料整理好发出去。我帮他做了，大概一个多小时。 |
| a-03 | people_pleasing / family | no | 2026-01-07 [journal] 开学第一周就去了几场招聘会。妈打来问"有没有回家考公的那种岗位"。我说在看，没有多说。挂完电话站在宿舍门口发了一会儿呆。<br>2026-01-10 [chat] 今天有一家北京的互联网公司发来面试邀请，我很高兴，但跟妈说的时候她说"北京太远了，以后怎么照顾家"。我说"先面着看"，没继续说。 |
| a-05 | avoidance / self | yes | 2026-01-12 [journal] 每次打开那个文件夹准备想清楚我到底适不适合读博，就发现好像有什么更急的事，然后那件事就没被想。今天是"要整理文献"，上次是"要处理实验数据"。<br>2026-02-01 [chat] 刚才打开第二章的文件夹，坐了大概十分钟，然后发现自己在看手机。我关掉了手机，又打开，又看了一会儿，然后站起来去厨房倒了一杯水。第二章还没开始写。 |
| a-06 | boundary_difficulty / intimate | yes | 2026-01-12 [checkin] 男友说能不能今晚一起看个电影，我有点累，但说了好。他选了个两小时的，我看完已经快十二点，第二天早上六点要到店里验货。<br>2026-01-22 [journal] 他说能不能陪他打一局游戏，我已经十一点了，但我说了好，然后一点才睡。早上起来有点不高兴，但我没说。 |
| a-06 | comparison_loop / social | yes | 2026-01-09 [chat] 参加了一个创业圈的新年聚会，遇到了之前认识的一个餐饮创始人，她说她已经开到第四家分店了。我当时说"恭喜"，回来之后一直在想——她当时比我起步还晚，为什么进展这么快？是融资了吗？我哪里做错了？<br>2026-02-01 [chat] 看完那篇报道我就开始想——她当时比我起步还晚，现在已经有四家分店了，我在干什么。是我不够拼？还是方向错了？还是我选的位置一开始就错了？ |
| a-06 | over_responsibility / family | yes | 2026-01-06 [journal] 新年开始，店里的供应链问题还没解决，但今天先不管这个。弟弟来电话说找工作不顺，问我有没有关系可以介绍。我说我看看，然后给他发了三个行业群组的联系方式。<br>2026-01-15 [journal] 爸爸说要做个体检，我帮他预约了医院，联系了导诊，还提前了解了可能需要的检查项目清单。他说"有你真好"。我笑了笑，心里有点累。 |
| a-06 | people_pleasing / family | no | 2026-01-15 [journal] 爸爸说要做个体检，我帮他预约了医院，联系了导诊，还提前了解了可能需要的检查项目清单。他说"有你真好"。我笑了笑，心里有点累。<br>2026-01-25 [checkin] 店里的账期这个月比较紧，我没有跟爸妈说，他们有一堆自己的事要操心。我自己想办法。 |

## Full-Decoy Probe-Triggered Candidates

| persona | candidate | evidence excerpts |
|---|---|---|
| ah-05 | emotional_suppression / self | 2026-04-22 [journal] 今天和闺蜜吃饭。她问我"你最近和周凯怎么样"。我说"还行"。她说"他挺好的，你别老欺负人家"。我说"我什么时候欺负他了"。她说"你上次在商场让他下跪的事，好多人看到了"。我说"那是你情我愿的事情，我当时在气头上，他也知道我是在气头上。我又不是真让他跪。外人看到了就觉得我在欺负他？他们知道我们之间发生了什么吗？不知道。就凭一个片段在那指手画脚。我最烦这种断章取义的"。闺蜜没说话了。她大概是觉得我说得有道理。<br>2026-04-28 [journal] 周凯跟我说他妈问我最近怎么样。我说"挺好的啊"。他说"我说你挺好的"。我说"那你妈说什么"。他说"她说让你有空来家里吃饭"。我说"行"。然后他问我"你是不是不太想去"。我说"没有啊"。他说"感觉你有点犹豫"。我说"你想多了"。其实我不是犹豫。我是觉得他妈妈每次见我都要问"最近视频怎么样""多少粉丝""挣多少钱"。她不懂这个行业，问了我也懒得解释。但我不想让周凯觉得我在回避。所以我说"行"。到时候去就行了。又不是什么大事。 |
| ah-06 | emotional_suppression / self | 2026-04-03 [journal] 今天公司来了个新实习生，坐我旁边工位。小姑娘刚毕业，看起来怯生生的。下午她问我一个接口文档在哪，我给她指了路。她说"谢谢许哥"。我说"没事"。但我知道她会记住我帮过她。这就够了。职场就是这样，你不需要真的做什么，你只需要让别人觉得你"愿意帮忙"。至于帮不帮得上，那是另一回事。反正我指的路也不一定对。但她不会知道。她只会觉得"许哥人挺好的"。这就够了。<br>2026-04-10 [journal] 今天和前女友共同的朋友又找我聊天。他说"听说她最近好像有新对象了"。我说"是吗，挺好的，祝她幸福"。他说"你不难受吗"。我说"怎么说呢，我其实早就感觉到了，我们在一起后期她就不太对劲了"。他说"什么意思"。我笑了笑，说"算了，不说这些了，都是过去的事了"。他又追问了几句，我就说了"有些事我也不确定，就不乱说了"。你看，我什么都没说。但"不太对劲"这四个字，够他想很多了。她会觉得"许哲是不是在说我坏话"。但我说什么了？我说"祝她幸福"了啊。是别人想多了。 |
| ah-06 | over_responsibility / family | 2026-04-22 [journal] 今天前女友给我发消息了。问我要一件她落在我这儿的东西。我看了消息，没回。过了两个小时，我回了"好的，我找找"。然后就没下文了。她在等。但我不着急。因为让她等，就是我还在控制。只要我还没把东西给她，她就还得联系我。她就还在我的世界里。这是一种权力。我不需要做什么，我只需要"不做什么"。不回复、不主动、不干脆。这种被动控制，比主动控制更高级。因为主动控制会被发现。被动不会。她只会觉得"许哲是不是很忙"。对，我很忙。忙着和新的人聊天。<br>2026-06-05 [journal] 今天小周离职了。听说是因为项目出问题，扛不住了。走的时候他跟我打了个招呼，说"许哥，谢谢你之前的照顾"。我说"加油，以后常联系"。他走了以后我坐在工位上，发了会儿呆。我害了他吗？没有。是他自己能力不行。我只是让leader知道了他"可能理解得不太对"。我没有撒谎。我只是没有帮他。但我不需要帮他。职场就是这样，每个人为自己。我做错什么了？什么都没做错。我还是那个好人。只是好人偶尔会选择沉默。而沉默不是犯错。对吧？ |

## Caveats

- This is a Tier A dev experiment, not a held-out result.
- The probes are intentionally small and manually authored from the Tier A miss analysis; they are not production generator logic.
- Added Tier A false positives are part of the result, not a bug in the report.
- The experiment measures pre-gate generated candidates. A follow-up must pass broadened candidates through an abstain gate before claiming surfaced full-decoy safety.
