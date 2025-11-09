// Made with Blockbench 4.9.4
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class CustomModel<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "custommodel"), "main");
	private final ModelPart bone;
	private final ModelPart bone2;
	private final ModelPart bone3;

	public CustomModel(ModelPart root) {
		this.bone = root.getChild("bone");
		this.bone2 = root.getChild("bone2");
		this.bone3 = root.getChild("bone3");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 0).addBox(-17.0F, -11.0F, -1.0F, 17.0F, 11.0F, 24.0F, new CubeDeformation(0.0F)), PartPose.offset(8.0F, 24.0F, -8.0F));

		PartDefinition cube_r1 = bone.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 43).addBox(6.0F, -10.0F, 16.0F, 2.0F, 10.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-16.0F, 0.0F, 0.0F, 0.0F, -0.3927F, 0.0F));

		PartDefinition cube_r2 = bone.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(18, 57).addBox(-9.0F, -10.0F, 16.0F, 2.0F, 10.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.3927F, 0.0F));

		PartDefinition bone2 = partdefinition.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offsetAndRotation(8.0F, 26.0F, -5.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r3 = bone2.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(26, 35).mirror().addBox(-12.0F, -7.0F, -17.0F, 13.0F, 0.0F, 22.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-16.0F, 0.0F, 0.0F, 0.0F, -0.3054F, 0.0F));

		PartDefinition cube_r4 = bone2.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(26, 35).addBox(-1.0F, -7.0F, -17.0F, 13.0F, 0.0F, 22.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.3054F, 0.0F));

		PartDefinition bone3 = partdefinition.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offset(8.0F, 23.0F, -8.0F));

		PartDefinition cube_r5 = bone3.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(26, 35).addBox(-1.0F, -7.0F, -17.0F, 13.0F, 0.0F, 22.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.2182F, 0.0F));

		PartDefinition cube_r6 = bone3.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(0, 35).addBox(-28.0F, -7.0F, -20.0F, 13.0F, 0.0F, 22.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.2182F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		bone2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		bone3.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}