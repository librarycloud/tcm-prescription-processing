import prisma from "../prisma.js";

// 获取所有法律文档配置 (后台或公开使用)
export const getLegalDocsController = async (request, reply) => {
  const configs = await prisma.systemConfig.findMany({
    where: {
      item: { in: ["privacy_policy", "user_agreement"] }
    }
  });

  const result = {
    privacy_policy: "",
    user_agreement: ""
  };

  configs.forEach(c => {
    result[c.item] = c.value;
  });

  reply.send(result);
};

// 后台保存法律文档配置
export const saveLegalDocsController = async (request, reply) => {
  const { privacy_policy, user_agreement } = request.body;

  if (privacy_policy !== undefined) {
    await prisma.systemConfig.upsert({
      where: { item: "privacy_policy" },
      update: { value: privacy_policy },
      create: {
        item: "privacy_policy",
        value: privacy_policy,
        class: "legal",
        isPublic: true,
        type: "markdown",
        mark: "隐私政策"
      }
    });
  }

  if (user_agreement !== undefined) {
    await prisma.systemConfig.upsert({
      where: { item: "user_agreement" },
      update: { value: user_agreement },
      create: {
        item: "user_agreement",
        value: user_agreement,
        class: "legal",
        isPublic: true,
        type: "markdown",
        mark: "用户协议"
      }
    });
  }

  reply.send({ message: "保存成功" });
};
