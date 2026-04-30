package com.github.brickwall2900.app;

import org.tinylog.Logger;

import java.util.Objects;

/**
 * ProgressIndicator helps manage the progress of tasks and the progress of subtasks.
 */
public class ProgressIndicator implements AutoCloseable {
    public static final ProgressIndicator VOID = new ProgressIndicator(ProgressUpdater.VOID);
    private ProgressUpdater updater;
    private long progress, maxProgress;
    private ProgressIndicator child, parent;
    private String taskDescription;
    private final boolean isRoot;
    private long lastUpdate = 0;

    private ProgressHelperListener listener;
    private boolean isCompleted;

    public ProgressIndicator(ProgressUpdater updater) {
        this(updater, null);
    }

    ProgressIndicator(ProgressUpdater updater, ProgressIndicator parent) {
        this.updater = Objects.requireNonNull(updater);
        this.parent = parent;
        this.progress = 0;
        this.maxProgress = 100;
        this.isRoot = parent == null;
    }

    public void updateProgress(String task, long progress) {
        assert !isCompleted : "???!??!?!!??";
        if (progress != -1 && (progress >= -1 && progress > maxProgress)) {
            throw new IndexOutOfBoundsException("Progress out of range!");
        }

        long oldProgress = this.progress;
        String oldDescription = this.taskDescription;

        this.progress = progress;
        this.taskDescription = task;

        if (oldProgress != progress
                && !Objects.equals(oldDescription, task)
                && (System.currentTimeMillis() - lastUpdate) > 30 /*ms*/) {
            updater.updateOverallProgress(this);
            lastUpdate = System.currentTimeMillis();
        }

        if (listener != null) {
            if (oldProgress != this.progress) {
                listener.onProgressChanged(this, oldProgress, this.progress);
            }
            if (!oldDescription.equals(this.taskDescription)) {
                listener.onTaskDescriptionChanged(this, oldDescription, this.taskDescription);
            }
        }
    }

    public void updateProgress(long progress) {
        updateProgress(taskDescription, progress);
    }

    public void setMaxProgress(long maxProgress) {
        if (maxProgress <= 0) {
            throw new IllegalArgumentException("Max progress must be positive.");
        }
        long oldMaxProgress = this.maxProgress;
        this.maxProgress = maxProgress;

        if (listener != null && oldMaxProgress != this.maxProgress) {
            listener.onMaxProgressChanged(this, oldMaxProgress, this.maxProgress);
        }
    }

    public long getProgress() {
        return progress;
    }

    public long getMaxProgress() {
        return maxProgress;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public ProgressIndicator getParent() {
        return parent;
    }

    public ProgressIndicator getChild() {
        return child;
    }

    public boolean isRoot() {
        return isRoot;
    }

    /**
     * Fork a ProgressIndicator as a child, making the ProgressIndicator child a parent to this ProgressIndicator,
     * effectively marking that a subtask.
     * @param childDescription the child task description
     * @param childMaxProgress the child's maximum progress
     * @return the child or subtask ProgressIndicator
     */
    public ProgressIndicator fork(String childDescription, long childMaxProgress) {
        assert child == null : "ProgressIndicator already has child you can't make another one " + child; // china mode
        ProgressIndicator childHelper = new ProgressIndicator(this.updater, this);
        childHelper.setMaxProgress(childMaxProgress);
        childHelper.taskDescription = childDescription;
        childHelper.updater.updateOverallProgress(childHelper);
        child = childHelper;
        Logger.trace("Forked {}", childHelper);
        return childHelper;
    }

    /**
     * Called by a child when it completes. The parent can use this to update its state.
     * @param child The child ProgressHelper that just completed.
     */
    protected void childTaskCompleted(ProgressIndicator child) {
        // This is where a parent would acknowledge a child's completion.
        // For example, if you have a list of children, you might check if all children are completed.
        // Or, if the parent's progress is based on completing certain stages, it would advance.

        // For simplicity, we'll just re-report our own state to the main updater
        // when a child completes. More complex logic would adjust parent's progress directly.
        updater.updateOverallProgress(this);

        // If all children are complete and this parent itself is conceptual, you might close the parent.
        // For instance, if this is a "Multi-Part Download" helper, and all parts are done, you close it.
        // Be careful not to create infinite loops if parent closing triggers child closing.
    }

    public void close() {
        Logger.trace("Closed {}", this);
        assert !isCompleted : "Attempt to close already completed ProgressHelper";

        if (progress < maxProgress) {
            updateProgress(maxProgress);
            updater.updateOverallProgress(this);
        }
        isCompleted = true;
        if (listener != null) {
            listener.onTaskCompleted(this);
        }
        if (parent != null) {
            parent.childTaskCompleted(this);
            parent.child = null;
        }
        Logger.trace("Notified parent {}", parent);
        parent = null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(taskDescription);
        ProgressIndicator helper = this.parent;
        while (helper != null) {
            builder.append(" <- ").append(helper.taskDescription);
            helper = helper.parent;
        }
        return builder.toString();
    }

    /**
     * Interface for the external component responsible for actually updating the UI progress bar.
     * This decouples the ProgressHelper logic from Swing/UI components.
     */
    public interface ProgressUpdater {
        /**
         * Called by a ProgressHelper to signal that its state has changed.
         * The implementation of this method is responsible for calculating the overall progress
         * and updating some actual UI component.
         * @param source the ProgressHelper that just updated its state.
         */
        void updateOverallProgress(ProgressIndicator source);

        /**
         * A ProgressUpdater that signals updates to the process into the void.
         */
        ProgressUpdater VOID = s -> {};
    }

    /**
     * Custom listener interface for ProgressHelper's internal state changes.
     */
    public interface ProgressHelperListener {
        void onProgressChanged(ProgressIndicator source, long oldProgress, long newProgress);
        void onMaxProgressChanged(ProgressIndicator source, long oldMaxProgress, long newMaxProgress);
        void onTaskDescriptionChanged(ProgressIndicator source, String oldDescription, String newDescription);
        void onTaskCompleted(ProgressIndicator completedHelper);
    }
}
