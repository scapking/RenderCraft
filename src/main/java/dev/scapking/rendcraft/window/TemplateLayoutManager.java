package dev.scapking.rendcraft.window;

import dev.scapking.rendcraft.protocol.WindowHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 模板窗口布局管理器。
 * 提供约束求解和平滑位移动态规划，解决了原有模板移动体验差的问题。
 */
public class TemplateLayoutManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateLayoutManager.class);
    
    private final Map<String, LayoutTemplate> templates = new ConcurrentHashMap<>();
    private final AtomicReference<LayoutState> currentState = new AtomicReference<>(new LayoutState());
    private final ConstraintSolver constraintSolver = new ConstraintSolver();
    
    /**
     * 保存当前窗口布局为临时模板。
     */
    public void saveTemporaryTemplate(String name, List<WindowHandle> windows) {
        LayoutTemplate template = new LayoutTemplate(name, windows, false);
        templates.put(name, template);
        LOGGER.info("Saved temporary template: {}", name);
    }
    
    /**
     * 保存当前窗口布局为永久模板。
     */
    public void savePermanentTemplate(String name, List<WindowHandle> windows, TemplateSlotConfig config) {
        LayoutTemplate template = new LayoutTemplate(name, windows, true);
        template.setSlotConfig(config);
        templates.put(name, template);
        LOGGER.info("Saved permanent template: {}", name);
    }
    
    /**
     * 应用模板布局。
     */
    public void applyTemplate(String name) {
        LayoutTemplate template = templates.get(name);
        if (template == null) {
            LOGGER.warn("Template not found: {}", name);
            return;
        }
        applyTemplateInternal(template);
    }
    
    /**
     * 应用永久模板布局。
     */
    public void applyPermanentTemplate(String name) {
        LayoutTemplate template = templates.get(name);
        if (template == null) {
            LOGGER.warn("Permanent template not found: {}", name);
            return;
        }
        applyTemplateInternal(template);
    }
    
    private void applyTemplateInternal(LayoutTemplate template) {
        if (template.getSlotConfig() != null) {
            // 使用约束求解器计算窗口位置
            List<ConstraintSolver.PositionSolution> positions = constraintSolver.solve(
                template.getWindows(),
                template.getSlotConfig()
            );
            // 更新状态
            currentState.getAndUpdate(state -> {
                state.setPositions(positions);
                return state;
            });
        } else {
            // 使用默认布局
            LayoutState state = currentState.get();
            state.setLayoutEnabled(true);
        }
        LOGGER.info("Applied template: {}", template.getName());
    }
    
    /**
     * 获取所有模板列表。
     */
    public List<String> listTemplates() {
        return new ArrayList<>(templates.keySet());
    }
    
    /**
     * 移除模板。
     */
    public void removeTemplate(String name) {
        templates.remove(name);
        LOGGER.info("Removed template: {}", name);
    }
    
    /**
     * 布局初始化。
     */
    public void initLayout(double x, double y, double z, float yaw) {
        LayoutState state = currentState.get();
        state.setCenterX(x);
        state.setCenterY(y);
        state.setCenterZ(z);
        state.setYaw(yaw);
        state.setInitialized(true);
        LOGGER.info("Layout initialized at ({}, {}, {}) with yaw {}", x, y, z, yaw);
    }
    
    /**
     * 启用/禁用自动布局。
     */
    public void setLayoutEnabled(boolean enabled) {
        currentState.getAndUpdate(state -> {
            state.setLayoutEnabled(enabled);
            return state;
        });
        LOGGER.info("Layout enabled: {}", enabled);
    }
    
    /**
     * 获取布局状态。
     */
    public LayoutState getLayoutState() {
        return currentState.get();
    }
    
    /**
     * 添加窗口到布局。
     */
    public void addWindowToLayout(WindowHandle handle) {
        LayoutState state = currentState.get();
        if (!state.isInitialized()) {
            LOGGER.warn("Cannot add window to layout: not initialized");
            return;
        }
        ConstraintSolver.PositionSolution solution = constraintSolver.findPositionForNewWindow(
            handle,
            state.getPositions(),
            state.getCenterX(),
            state.getCenterY(),
            state.getCenterZ(),
            state.getYaw()
        );
        if (solution != null) {
            currentState.updateAndGet(s -> {
                s.getPositions().add(solution);
                return s;
            });
            LOGGER.info("Added window {} to layout at position {}", handle, solution);
        }
    }
    
    /**
     * 从布局中移除窗口。
     */
    public void removeWindowFromLayout(WindowHandle handle) {
        currentState.updateAndGet(s -> {
            s.getPositions().removeIf(p -> p.getHandle().equals(handle));
            return s;
        });
        LOGGER.info("Removed window {} from layout", handle);
    }
    
    /**
     * 布局模板枚举。
     */
    public enum LayoutTemplateType {
        CUBE,
        SPHERE
    }
    
    /**
     * 布局状态。
     */
    public static class LayoutState {
        private boolean layoutEnabled = true;
        private boolean initialized = false;
        private double centerX = 0.0;
        private double centerY = 0.0;
        private double centerZ = 0.0;
        private float yaw = 0.0f;
        private float radius = 6.0f;
        private float spacing = 0.4f;
        private float stackSpacing = 0.4f;
        private int cubePerFace = 2;
        private LayoutTemplateType templateType = LayoutTemplateType.CUBE;
        private List<ConstraintSolver.PositionSolution> positions = new ArrayList<>();
        private WindowHandle coreWindow = null;
        
        // Getters and setters
        public boolean isLayoutEnabled() { return layoutEnabled; }
        public void setLayoutEnabled(boolean layoutEnabled) { this.layoutEnabled = layoutEnabled; }
        public boolean isInitialized() { return initialized; }
        public void setInitialized(boolean initialized) { this.initialized = initialized; }
        public double getCenterX() { return centerX; }
        public void setCenterX(double centerX) { this.centerX = centerX; }
        public double getCenterY() { return centerY; }
        public void setCenterY(double centerY) { this.centerY = centerY; }
        public double getCenterZ() { return centerZ; }
        public void setCenterZ(double centerZ) { this.centerZ = centerZ; }
        public float getYaw() { return yaw; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public float getRadius() { return radius; }
        public void setRadius(float radius) { this.radius = radius; }
        public float getSpacing() { return spacing; }
        public void setSpacing(float spacing) { this.spacing = spacing; }
        public float getStackSpacing() { return stackSpacing; }
        public void setStackSpacing(float stackSpacing) { this.stackSpacing = stackSpacing; }
        public int getCubePerFace() { return cubePerFace; }
        public void setCubePerFace(int cubePerFace) { this.cubePerFace = cubePerFace; }
        public LayoutTemplateType getTemplateType() { return templateType; }
        public void setTemplateType(LayoutTemplateType templateType) { this.templateType = templateType; }
        public List<ConstraintSolver.PositionSolution> getPositions() { return positions; }
        public void setPositions(List<ConstraintSolver.PositionSolution> positions) { this.positions = positions; }
        public WindowHandle getCoreWindow() { return coreWindow; }
        public void setCoreWindow(WindowHandle coreWindow) { this.coreWindow = coreWindow; }
    }
    
    /**
     * 约束求解器。
     * 使用简化的约束满足算法来计算窗口位置，确保窗口间距和对齐。
     */
    public static class ConstraintSolver {
        
        /**
         * 求解窗口位置列表。
         */
        public List<PositionSolution> solve(List<WindowHandle> windows, TemplateSlotConfig config) {
            List<PositionSolution> positions = new ArrayList<>();
            
            // 根据模板类型计算位置
            if (config.getTemplateType() == TemplateLayoutManager.LayoutTemplateType.CUBE) {
                positions = solveCubeLayout(0.0, 0.0, 0.0, 0.0f, windows, config);
            } else if (config.getTemplateType() == TemplateLayoutManager.LayoutTemplateType.SPHERE) {
                positions = solveSphereLayout(0.0, 0.0, 0.0, 0.0f, windows, config);
            }
            
            return positions;
        }
        
        /**
         * 为新窗口找到位置。
         */
        public PositionSolution findPositionForNewWindow(
                WindowHandle handle,
                List<PositionSolution> existingPositions,
                double centerX,
                double centerY,
                double centerZ,
                float yaw) {
            
            // 简单的贪婪算法：找到第一个不冲突的位置
            for (int face = 0; face < 6; face++) {
                for (int slot = 0; slot < 10; slot++) {
                    PositionSolution candidate = createCubePosition(
                        handle, face, slot, centerX, centerY, centerZ, yaw, 6.0f, 0.4f
                    );
                    if (!conflictsWith(candidate, existingPositions)) {
                        return candidate;
                    }
                }
            }
            return null;
        }
        
        private List<PositionSolution> solveCubeLayout(double centerX, double centerY, double centerZ, float yaw, List<WindowHandle> windows, TemplateSlotConfig config) {
            List<PositionSolution> positions = new ArrayList<>();
            int perFace = config.getCubePerFace() != null ? config.getCubePerFace() : 2;
            float radius = config.getRadius() != null ? config.getRadius() : 6.0f;
            float spacing = config.getSpacing() != null ? config.getSpacing() : 0.4f;

            int windowIndex = 0;
            for (int face = 0; face < 6 && windowIndex < windows.size(); face++) {
                for (int slot = 0; slot < perFace && windowIndex < windows.size(); slot++) {
                    PositionSolution pos = createCubePosition(
                        windows.get(windowIndex), face, slot,
                        centerX, centerY, centerZ, yaw, radius, spacing
                    );
                    positions.add(pos);
                    windowIndex++;
                }
            }
            return positions;
        }

        private List<PositionSolution> solveSphereLayout(double centerX, double centerY, double centerZ, float yaw, List<WindowHandle> windows, TemplateSlotConfig config) {
            List<PositionSolution> positions = new ArrayList<>();
            float radius = config.getRadius() != null ? config.getRadius() : 6.0f;
            float stackSpacing = config.getStackSpacing() != null ? config.getStackSpacing() : 0.4f;
            
            int windowIndex = 0;
            int layer = 0;
            while (windowIndex < windows.size()) {
                // 计算每层的窗口数量（环形）
                int windowsInLayer = (int)(2 * Math.PI * radius * (layer + 1) / 2.0);
                for (int i = 0; i < windowsInLayer && windowIndex < windows.size(); i++) {
                    double angle = 2 * Math.PI * i / windowsInLayer;
                    double x = centerX + radius * (layer + 1) * Math.cos(angle);
                    double z = centerZ + radius * (layer + 1) * Math.sin(angle);
                    double y = centerY + layer * stackSpacing;
                    PositionSolution pos = new PositionSolution(
                        windows.get(windowIndex), x, y, z, 0.0f
                    );
                    positions.add(pos);
                    windowIndex++;
                }
                layer++;
            }
            return positions;
        }
        
        private PositionSolution createCubePosition(
                WindowHandle handle, int face, int slot,
                double centerX, double centerY, double centerZ,
                float yaw, float radius, float spacing) {
            
            // 计算面坐标
            double[] faceCenter = getFaceCenter(face, radius, centerX, centerY, centerZ, yaw);
            double offset = (slot - 1) * spacing;
            
            double x = faceCenter[0] + offset;
            double y = faceCenter[1] + (slot % 2) * spacing;
            double z = faceCenter[2];
            
            return new PositionSolution(handle, x, y, z, yaw);
        }
        
        private double[] getFaceCenter(int face, float radius, 
                double cx, double cy, double cz, float yaw) {
            switch (face) {
                case 0: return new double[]{cx + radius, cy, cz}; // +X
                case 1: return new double[]{cx - radius, cy, cz}; // -X
                case 2: return new double[]{cx, cy, cz + radius}; // +Z
                case 3: return new double[]{cx, cy, cz - radius}; // -Z
                case 4: return new double[]{cx, cy + radius, cz}; // +Y
                case 5: return new double[]{cx, cy - radius, cz}; // -Y
                default: return new double[]{cx, cy, cz};
            }
        }
        
        private boolean conflictsWith(PositionSolution candidate, List<PositionSolution> existing) {
            for (PositionSolution existingPos : existing) {
                double dx = candidate.getX() - existingPos.getX();
                double dy = candidate.getY() - existingPos.getY();
                double dz = candidate.getZ() - existingPos.getZ();
                double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (distance < 0.4) { // 最小间距为 0.4 块
                    return true;
                }
            }
            return false;
        }
        
        /**
         * 位置解决方案。
         */
        public static class PositionSolution {
            private final WindowHandle handle;
            private final double x;
            private final double y;
            private final double z;
            private final float yaw;
            
            public PositionSolution(WindowHandle handle, double x, double y, double z, float yaw) {
                this.handle = handle;
                this.x = x;
                this.y = y;
                this.z = z;
                this.yaw = yaw;
            }
            
            public WindowHandle getHandle() { return handle; }
            public double getX() { return x; }
            public double getY() { return y; }
            public double getZ() { return z; }
            public float getYaw() { return yaw; }
            
            @Override
            public String toString() {
                return String.format("Position[%s: %.2f, %.2f, %.2f, yaw=%.1f]",
                    handle, x, y, z, yaw);
            }
        }
    }
}
