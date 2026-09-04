package dev.scapking.rendcraft.window;

import dev.scapking.rendcraft.protocol.WindowHandle;
import java.util.List;

/**
 * 模板槽位配置。
 * 用于定义模板中窗口的排列方式和约束条件。
 */
public class TemplateSlotConfig {
    private TemplateLayoutManager.LayoutTemplateType templateType;
    private Integer cubePerFace;
    private Float radius;
    private Float spacing;
    private Float stackSpacing;
    private Float defaultWidth;
    private Float defaultHeight;
    private List<WindowHandle> initialWindows;
    
    public TemplateSlotConfig() {
        this.templateType = TemplateLayoutManager.LayoutTemplateType.CUBE;
        this.cubePerFace = 2;
        this.radius = 6.0f;
        this.spacing = 0.4f;
        this.stackSpacing = 0.4f;
        this.defaultWidth = 1080.0f;
        this.defaultHeight = 540.0f;
    }
    
    public TemplateLayoutManager.LayoutTemplateType getTemplateType() {
        return templateType;
    }
    
    public void setTemplateType(TemplateLayoutManager.LayoutTemplateType templateType) {
        this.templateType = templateType;
    }
    
    public Integer getCubePerFace() {
        return cubePerFace;
    }
    
    public void setCubePerFace(Integer cubePerFace) {
        this.cubePerFace = cubePerFace;
    }
    
    public Float getRadius() {
        return radius;
    }
    
    public void setRadius(Float radius) {
        this.radius = radius;
    }
    
    public Float getSpacing() {
        return spacing;
    }
    
    public void setSpacing(Float spacing) {
        this.spacing = spacing;
    }
    
    public Float getStackSpacing() {
        return stackSpacing;
    }
    
    public void setStackSpacing(Float stackSpacing) {
        this.stackSpacing = stackSpacing;
    }
    
    public Float getDefaultWidth() {
        return defaultWidth;
    }
    
    public void setDefaultWidth(Float defaultWidth) {
        this.defaultWidth = defaultWidth;
    }
    
    public Float getDefaultHeight() {
        return defaultHeight;
    }
    
    public void setDefaultHeight(Float defaultHeight) {
        this.defaultHeight = defaultHeight;
    }
    
    public List<WindowHandle> getInitialWindows() {
        return initialWindows;
    }
    
    public void setInitialWindows(List<WindowHandle> initialWindows) {
        this.initialWindows = initialWindows;
    }
}
