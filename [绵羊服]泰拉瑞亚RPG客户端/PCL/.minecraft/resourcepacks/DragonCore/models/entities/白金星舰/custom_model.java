// Made with Blockbench 4.7.4
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class custom_model<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "custom_model"), "main");
	private final ModelPart bone5;
	private final ModelPart bone6;
	private final ModelPart bb_main;

	public custom_model(ModelPart root) {
		this.bone5 = root.getChild("bone5");
		this.bone6 = root.getChild("bone6");
		this.bb_main = root.getChild("bb_main");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone5 = partdefinition.addOrReplaceChild("bone5", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition bone = bone5.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(274, 211).addBox(-17.0F, -16.0F, -11.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-35.0F, -16.0F, -8.0F, 0.0F, 0.5236F, 0.0F));

		PartDefinition cube_r1 = bone.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 268).addBox(-14.0F, -49.0F, -18.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(198, 188).addBox(-21.0F, -34.0F, -19.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r2 = bone.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(224, 102).addBox(-22.0F, -25.0F, -19.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r3 = bone.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(80, 290).addBox(-16.0F, -2.0F, -10.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition bone2 = bone5.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(44, 272).addBox(8.0F, -16.0F, -11.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(35.0F, -16.0F, -8.0F, 0.0F, -0.5236F, 0.0F));

		PartDefinition cube_r4 = bone2.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(88, 250).addBox(10.0F, -49.0F, -18.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(126, 188).addBox(3.0F, -34.0F, -19.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r5 = bone2.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(220, 164).addBox(2.0F, -25.0F, -19.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r6 = bone2.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(282, 84).addBox(10.0F, -2.0F, -10.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition bone3 = bone5.addOrReplaceChild("bone3", CubeListBuilder.create().texOffs(270, 186).addBox(-17.0F, -16.0F, -11.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(-7.0F, -19.0F, -28.0F, 0.0F, 0.5236F, 0.0F));

		PartDefinition cube_r7 = bone3.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(248, 228).addBox(-14.0F, -49.0F, -18.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(2.0F))
		.texOffs(166, 80).addBox(-21.0F, -34.0F, -19.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r8 = bone3.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(220, 62).addBox(-22.0F, -25.0F, -19.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r9 = bone3.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(280, 16).addBox(-16.0F, -2.0F, -10.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition bone4 = bone5.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(240, 268).addBox(8.0F, -16.0F, -11.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(7.0F, -19.0F, -28.0F, 0.0F, -0.5236F, 0.0F));

		PartDefinition cube_r10 = bone4.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(44, 232).addBox(10.0F, -49.0F, -18.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(2.0F))
		.texOffs(72, 166).addBox(3.0F, -34.0F, -19.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r11 = bone4.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(220, 22).addBox(2.0F, -25.0F, -19.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r12 = bone4.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(278, 149).addBox(10.0F, -2.0F, -10.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition bone6 = partdefinition.addOrReplaceChild("bone6", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 34.0F));

		PartDefinition bone7 = bone6.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(204, 268).addBox(-17.0F, -16.0F, 2.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-33.0F, -16.0F, 8.0F, 0.0F, -0.5236F, 0.0F));

		PartDefinition cube_r13 = bone7.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(204, 228).addBox(-14.0F, -49.0F, 0.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(166, 40).addBox(-21.0F, -34.0F, 1.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r14 = bone7.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(58, 210).addBox(-22.0F, -25.0F, 1.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r15 = bone7.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(278, 44).addBox(-16.0F, -2.0F, 4.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition bone8 = bone6.addOrReplaceChild("bone8", CubeListBuilder.create().texOffs(168, 268).addBox(8.0F, -16.0F, 2.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(35.0F, -16.0F, 8.0F, 0.0F, 0.5236F, 0.0F));

		PartDefinition cube_r16 = bone8.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(160, 228).addBox(10.0F, -49.0F, 0.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(166, 0).addBox(3.0F, -34.0F, 1.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r17 = bone8.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(0, 206).addBox(2.0F, -25.0F, 1.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r18 = bone8.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(276, 268).addBox(10.0F, -2.0F, 4.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition bone9 = bone6.addOrReplaceChild("bone9", CubeListBuilder.create().texOffs(132, 268).addBox(-17.0F, -16.0F, 2.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(-7.0F, -19.0F, 28.0F, 0.0F, -0.5236F, 0.0F));

		PartDefinition cube_r19 = bone9.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(116, 228).addBox(-14.0F, -49.0F, 0.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(2.0F))
		.texOffs(0, 166).addBox(-21.0F, -34.0F, 1.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r20 = bone9.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(202, 142).addBox(-22.0F, -25.0F, 1.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r21 = bone9.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(262, 0).addBox(-16.0F, -2.0F, 4.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition bone10 = bone6.addOrReplaceChild("bone10", CubeListBuilder.create().texOffs(260, 124).addBox(8.0F, -16.0F, 2.0F, 9.0F, 16.0F, 9.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(7.0F, -19.0F, 28.0F, 0.0F, 0.5236F, 0.0F));

		PartDefinition cube_r22 = bone10.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(0, 228).addBox(10.0F, -49.0F, 0.0F, 4.0F, 22.0F, 18.0F, new CubeDeformation(2.0F))
		.texOffs(148, 148).addBox(3.0F, -34.0F, 1.0F, 18.0F, 22.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2618F, 0.0F, 0.0F));

		PartDefinition cube_r23 = bone10.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(166, 120).addBox(2.0F, -25.0F, 1.0F, 20.0F, 4.0F, 18.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r24 = bone10.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(238, 0).addBox(10.0F, -2.0F, 4.0F, 6.0F, 16.0F, 6.0F, new CubeDeformation(2.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(0, 83).addBox(-31.0F, -112.0F, -10.0F, 70.0F, 70.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-31.0F, -112.0F, 29.0F, 70.0F, 70.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(-13, 70).addBox(-31.0F, -112.0F, 3.0F, 10.0F, 70.0F, 26.0F, new CubeDeformation(0.0F))
		.texOffs(-13, 70).mirror().addBox(29.0F, -112.0F, 3.0F, 10.0F, 70.0F, 26.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 24.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 512, 512);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		bone5.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		bone6.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		bb_main.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}