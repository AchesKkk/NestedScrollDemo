package com.sheldonchen.nestedscrolldemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.ListViewCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.OverScroller;
import android.widget.ScrollView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 支持嵌套滚动的容器-NestedScrollLayout.
 * note：
 *      需要指定相应的头部Header id以及嵌套滚动的子View id
 *      eg:
 *          app:header="@+id/layout_header"
 *          app:scroll_child="@+id/layout_scroll_child"
 *
 *      嵌套滚动的子View通常可以是ListView、RecyclerView、ScrollView等,
 *      其它嵌套滚动可通过实现接口 {@link OnChildScrollCallback}辅助解决嵌套滑动的问题.
 * <p>
 * Created by cxd on 2018/6/13
 */

public class NestedScrollLayout extends ViewGroup {

    private int mHeaderId;
    private int mScrollChildId;
    private int mHeaderIndex = -1;
    private int mScrollChildIndex = -1;

    private View mHeaderView;
    /**
     * 嵌套的可滚动子View.
     */
    private View mScrollChildView;

    private OnChildScrollCallback mChildScrollCallback;

    private boolean mHasAttrs;

    private static final int INVALID_POINTER = -1;
    private int mActivePointerId;

    private boolean mIsDragging;
    private float mInitialDownX;
    private float mInitialDownY;
    private float mLastMotionY;
    private float mTouchSlop;
    private float mMaxVelocity;

    private VelocityTracker mVelocityTracker;
    private OverScroller mScroller;

    private Rect mTempRect = new Rect();

    /**
     * Header滚动系数(可实现错位滚动).
     */
    private float mHeaderScrollRatio = 0.5F;


    // constructors.

    public NestedScrollLayout(Context context) {
        super(context);
        init(context, null);
    }

    public NestedScrollLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public NestedScrollLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) {
            mHasAttrs = false;
        } else {
            mHasAttrs = true;

            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.NestedScrollLayout);
            mHeaderId = typedArray.getResourceId(R.styleable.NestedScrollLayout_header, 0);
            mScrollChildId = typedArray.getResourceId(R.styleable.NestedScrollLayout_scroll_child, 0);

            if (mHeaderId == 0 || mScrollChildId == 0) {
                mHasAttrs = false;
            }
            typedArray.recycle();
        }

        setChildrenDrawingOrderEnabled(true);

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop() * 0.25F;
        mMaxVelocity = configuration.getScaledMaximumFlingVelocity();

        mScroller = new OverScroller(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ensureTarget();
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        ensureTarget();

        if (mHeaderIndex == -1) {
            mHeaderIndex = indexOfChild(mHeaderView);
        }
        if (mScrollChildIndex == -1) {
            mScrollChildIndex = indexOfChild(mScrollChildView);
        }
        if (mHeaderIndex < mScrollChildIndex) {
            return i;
        } else {
            if (mHeaderIndex == i) {
                return mScrollChildIndex;
            } else if (mScrollChildIndex == i) {
                return mHeaderIndex;
            }
        }
        return i;
    }

    private void ensureTarget() {
        if (mHeaderView == null) {
            if (mHasAttrs) {
                mHeaderView = findViewById(mHeaderId);
            } else {
                // 如果没有指定Header id, 则默认Header是第一个子View.
                mHeaderView = getChildAt(0);
            }
            checkTargetViewNonNull(mHeaderView, "NestedScrollLayout: Can't find header!");
        }

        if (mScrollChildView == null) {
            if (mHasAttrs) {
                mScrollChildView = findViewById(mScrollChildId);
            } else {
                // 如果没有指定Scroll child id, 则默认ScrollChild是第二个子View.
                mScrollChildView = getChildAt(1);

            }
            checkTargetViewNonNull(mScrollChildView, "NestedScrollLayout: Can't find any scroll child!");
        }
    }

    private static void checkTargetViewNonNull(View targetView, String msg) {
        if (targetView == null) {
            throw new Resources.NotFoundException(msg);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ensureTarget();

        measureChild(mHeaderView, widthMeasureSpec, heightMeasureSpec);

        final int scrollChildWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY);
        final int scrollChildHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY);
        mScrollChildView.measure(scrollChildWidthMeasureSpec, scrollChildHeightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() == 0) {
            return;
        }
        ensureTarget();

        final int height = getMeasuredHeight();
        final int headerHeight = mHeaderView.getMeasuredHeight();
        final int headerWidth = mHeaderView.getMeasuredWidth();
        final int scrollChildWidth = mScrollChildView.getMeasuredWidth();

        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();

        final int headerBottom = paddingTop + headerHeight;

        mHeaderView.layout(paddingLeft, paddingTop
                , paddingLeft + headerWidth, headerBottom);
        mScrollChildView.layout(paddingLeft, headerBottom
                , paddingLeft + scrollChildWidth, headerHeight + height - getPaddingBottom());
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // 不允许子View请求不拦截触摸事件
        // 当前自顶控件需要执行#onInterceptTouchEvent(MotionEvent ev)拦截触摸事件进行处理.
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled() || canChildScrollUp()/*子View还可以向上滚动, 将触摸事件向子View分发*/) {
            return false;
        }

        final int action = ev.getActionMasked();
        int pointerIndex;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                resetScroller();
                mActivePointerId = ev.getPointerId(0);
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                mIsDragging = false;

                mInitialDownX = ev.getX(pointerIndex);
                mInitialDownY = ev.getY(pointerIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER) {
                    return false;
                }
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }
                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                startDragging(x, y);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragging = false;
                mActivePointerId = INVALID_POINTER;
                break;
        }

        return mIsDragging;
    }

    private void resetScroller() {
        if (mScroller == null || mScroller.isFinished()) {
            return;
        }

        mScroller.abortAnimation();
    }

    private void startDragging(float x, float y) {
        final float xDiff = Math.abs(x - mInitialDownX);
        final float yDiff = Math.abs(y - mInitialDownY);
        if (yDiff > xDiff && yDiff > mTouchSlop) {
            if (y > mInitialDownY
                    || mScrollChildView.getTop() > getPaddingTop()) {
                if (!mIsDragging) {
                    mLastMotionY = y;
                    mIsDragging = true;
                }
            }
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || canChildScrollUp()) {
            return false;
        }

        final int action = ev.getActionMasked();
        int pointerIndex;

        acquireVelocityTracker(ev);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsDragging = false;

                if (touchInHeaderRect(ev)) {
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                final float x = ev.getX(pointerIndex);
                final float y = ev.getY(pointerIndex);
                startDragging(x, y);

                if (mIsDragging) {
                    final float dy = y - mLastMotionY;
                    if (dy > 0) {
                        offsetChildren(dy);
                    } else if (dy < 0) {
                        offsetChildren(dy);
                        if (mScrollChildView.getTop() <= getPaddingTop()) {
                            final int oldAction = ev.getAction();
                            ev.setAction(MotionEvent.ACTION_DOWN);
                            dispatchTouchEvent(ev);
                            ev.setAction(oldAction);
                        }
                    }
                }

                mLastMotionY = y;
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                pointerIndex = ev.getActionIndex();
                if (pointerIndex < 0) {
                    return false;
                }
                mActivePointerId = ev.getPointerId(pointerIndex);

                mInitialDownX = ev.getX(pointerIndex);
                mLastMotionY = mInitialDownY = ev.getY(pointerIndex);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP: {
                pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                if (mIsDragging) {
                    mIsDragging = false;
                    mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
                    final float velocity = mVelocityTracker.getYVelocity(mActivePointerId);
                    startFlingIfNeed((int) velocity);
                }
                mActivePointerId = INVALID_POINTER;
                releaseVelocityTracker();
                return false;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseVelocityTracker();
                return false;
        }

        return mIsDragging;
    }

    private boolean touchInHeaderRect(MotionEvent event) {
        if (mHeaderView.getGlobalVisibleRect(mTempRect)) {
            final float rawX = event.getRawX();
            final float rawY = event.getRawY();

            if (mTempRect.contains((int) rawX, (int) rawY)) {
                return true;
            }
        }

        return false;
    }


    /**
     * 滚动到指定位置.
     */
    public void scrollTo(int y) {
        moveChildrenTo(y);
    }

    /**
     * 不直接展示Header, 直接让嵌套滚动的子View置顶.
     */
    public void scrollToNestedChild() {
        post(new Runnable() {
            @Override
            public void run() {
                moveChildrenTo(0);
            }
        });
    }

    public void scrollBy(int dy) {
        offsetChildren(dy);
    }

    /**
     * 此方法类似于#ScrollBy(int dy).
     */
    private void offsetChildren(float dy) {
        moveChildrenTo(getCurrentScrollY() - (int) dy);
    }

    public int getCurrentScrollY() {
        return mHeaderView.getMeasuredHeight() - mScrollChildView.getTop() + getPaddingTop();
    }

    private void moveChildrenTo(int scrollY) {
        final int paddingTop = getPaddingTop();
        final int headerHeight = mHeaderView.getMeasuredHeight();
        final int scrollChildTop = mScrollChildView.getTop();
        final int headerTop = mHeaderView.getTop();

        int offset;
        int headerOffset;
        if (scrollY <= 0) {
            offset = headerHeight + paddingTop - scrollChildTop;
            headerOffset = paddingTop - headerTop;
        } else if (scrollY >= headerHeight) {
            offset = paddingTop - scrollChildTop;
            headerOffset = Math.round(offset * mHeaderScrollRatio);
        } else {
            offset = paddingTop + headerHeight - scrollChildTop - scrollY;
            headerOffset = Math.round(offset * mHeaderScrollRatio);
            if((paddingTop - headerTop - headerOffset) < 0) {
                headerOffset = 0;
            }
        }

        ViewCompat.offsetTopAndBottom(mHeaderView, headerOffset);
        ViewCompat.offsetTopAndBottom(mScrollChildView, offset);
    }

    private void startFlingIfNeed(int velocity) {
        final int currentScrollY = getCurrentScrollY();

        if (velocity > 0) {
            mScroller.fling(0, currentScrollY, 0, -velocity
                    , 0, 0, 0, currentScrollY);
            invalidate();
        } else if (velocity < 0) {
            mScroller.fling(0, currentScrollY, 0, -velocity
                    , 0, 0, currentScrollY, Integer.MAX_VALUE);
            invalidate();
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            int currentY = mScroller.getCurrY();
            moveChildrenTo(currentY);
            dispatchFlingIfNeed(currentY);

            invalidate();
        }
    }

    private void dispatchFlingIfNeed(int currentY) {
        if(currentY >= mHeaderView.getMeasuredHeight()) {
            mScroller.abortAnimation();

            float velocityRemained;
            if ((velocityRemained = mScroller.getCurrVelocity()) > 0) {
                if (mChildScrollCallback != null) {
                    mChildScrollCallback.dispatchFlingVelocity(this, mScrollChildView
                            , velocityRemained);
                } else {
                    helpScrollChildFling(mScrollChildView, velocityRemained);
                }
            }
        }
    }

    private void acquireVelocityTracker(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * 设置嵌套滚动系数
     * 效果：Header滚动和嵌套滚动的子View滚动速度不一致, 呈现出一种平行错位的效果.
     */
    public void setHeaderScrollRatio(float ratio) {
        if (ratio < 0.F || ratio > 1.F) {
            return;
        }
        mHeaderScrollRatio = ratio;
    }

    /**
     * 判断子View是否可以向上滚动.
     */
    private boolean canChildScrollUp() {
        if (mChildScrollCallback != null) {
            return mChildScrollCallback.canChildScrollUp(this, mScrollChildView);
        }
        if (mScrollChildView instanceof ListView) {
            return ListViewCompat.canScrollList((ListView) mScrollChildView, -1);
        }
        return mScrollChildView.canScrollVertically(-1);
    }

    public void setOnChildScrollCallback(OnChildScrollCallback callback) {
        mChildScrollCallback = callback;
    }

    public interface OnChildScrollCallback {

        boolean canChildScrollUp(NestedScrollLayout parent, View child);

        /**
         * 当嵌套滚动子View滚动到顶部时, 若还有fling速度余量,
         * 可通过此方法将速度分发给子View继续滚动.
         */
        void dispatchFlingVelocity(NestedScrollLayout parent, View child, float velocity);
    }
    
    // helper.

    /**
     * 辅助Fling的工具方法.
     */
    public static void helpScrollChildFling(View child, float velocity) {
        if(child instanceof AbsListView) {
            AbsListView listView = (AbsListView) child;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                listView.fling((int) velocity);
            } else {
                flingListViewByReflection(listView, (int) velocity);
            }
        } else if(child instanceof RecyclerView) {
            RecyclerView recyclerView = (RecyclerView) child;
            recyclerView.fling(0, (int) velocity);
        } else if(child instanceof ScrollView) {
            ScrollView scrollView = (ScrollView) child;
            scrollView.fling((int) velocity);
        } else if(child instanceof NestedScrollView) {
            NestedScrollView nestedScrollView = (NestedScrollView) child;
            nestedScrollView.fling((int) velocity);
        }
        // WebView没有fling方法，暂不做考虑.
    }


    /**
     * 版本低于LOLLIPOP通过反射fling.
     */
    private static void flingListViewByReflection(AbsListView listView, int velocity) {
        try {
            Field flingRunnableField = AbsListView.class.getDeclaredField("mFlingRunnable");
            flingRunnableField.setAccessible(true);
            Object flingRunnable = flingRunnableField.get(listView);
            @SuppressLint("PrivateApi")
            Class<?> flingRunnableClazz = Class.forName("android.widget.AbsListView$FlingRunnable");
            if(flingRunnable == null) {
                Constructor<?> cs = flingRunnableClazz.getDeclaredConstructor(AbsListView.class);
                cs.setAccessible(true);
                flingRunnable = cs.newInstance(listView);
            }
            flingRunnableField.set(listView, flingRunnable);
            @SuppressLint("PrivateApi")
            Method reportScrollStateChangeMethod = AbsListView.class.getDeclaredMethod("reportScrollStateChange", int.class);
            reportScrollStateChangeMethod.setAccessible(true);
            reportScrollStateChangeMethod.invoke(listView, AbsListView.OnScrollListener.SCROLL_STATE_FLING);

            Method startMethod = flingRunnableClazz.getDeclaredMethod("start", int.class);
            startMethod.setAccessible(true);
            startMethod.invoke(flingRunnable, velocity);
        } catch (Exception e) {
            // nope.
        }
    }

    public static class ViewPagerFlingHelper implements OnChildScrollCallback {
        private ViewPager mViewPager;
        private SparseArray<View> mScrollChildContainer;

        private final Rect mTempRect = new Rect();

        public ViewPagerFlingHelper(ViewPager viewPager) {
            if(viewPager == null) {
                throw new IllegalArgumentException("ViewPager can't be null!");
            }
            mViewPager = viewPager;
            mScrollChildContainer = new SparseArray<>();
        }

        @Override
        public boolean canChildScrollUp(NestedScrollLayout parent, View child) {
            View scrollChild = getCurrentScrollChild();
            if(scrollChild == null) {
                return false;
            }

            if (scrollChild instanceof ListView) {
                return ListViewCompat.canScrollList((ListView) scrollChild, -1);
            }
            return scrollChild.canScrollVertically(-1);
        }

        @Override
        public void dispatchFlingVelocity(NestedScrollLayout parent, View child, float velocity) {
            View scrollChild = getCurrentScrollChild();
            if(scrollChild == null) {
                return;
            }

            helpScrollChildFling(scrollChild, velocity);
        }

        private View getCurrentScrollChild() {
            final int currentItem = mViewPager.getCurrentItem();
            View scrollChild = mScrollChildContainer.get(currentItem);

            if(scrollChild == null) {
                scrollChild = findCurrentShownScrollChild(mViewPager);
                if(scrollChild == null) return null;

                mScrollChildContainer.put(currentItem, scrollChild);
            }

            return scrollChild;
        }

        private ViewGroup findCurrentShownScrollChild(ViewGroup parent) {
            if(parent == null) return null;

            if(isCurrentShownChild(parent) && isScrollableView(parent)) {
                return parent;
            }

            for(int i = 0, count = parent.getChildCount(); i < count; i++) {
                View child = parent.getChildAt(i);
                if(child instanceof ViewGroup) {
                    ViewGroup target = findCurrentShownScrollChild((ViewGroup) child);
                    if(target != null) {
                        return target;
                    }
                }
            }
            return null;
        }

        private boolean isScrollableView(View target) {
            // 只判断目前通用的可滚动容器,
            // 自定义可滚动容器可通过实现#OnChildScrollCallback接口辅助解决嵌套滑动问题.
            return target instanceof AbsListView
                    || target instanceof RecyclerView
                    || target instanceof ScrollView
                    || target instanceof NestedScrollView
                    || target instanceof WebView;
        }

        private boolean isCurrentShownChild(View child) {
            if (child.getGlobalVisibleRect(mTempRect)) {
                if (mTempRect.width() >= child.getMeasuredWidth()
                        && mTempRect.height() >= child.getMeasuredHeight()) {
                    return true;
                }
            }
            return false;
        }

    }

}
