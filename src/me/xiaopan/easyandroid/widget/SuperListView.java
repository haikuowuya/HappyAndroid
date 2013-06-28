package me.xiaopan.easyandroid.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Scroller;

/**
 * 这是一个扩展是ListView，支持下拉刷新和滚动到底部自动加载更多
 */
public class SuperListView extends ListView implements OnScrollListener, GestureDetector.OnGestureListener, BasePulldownRefershListHeader.OnAllowReadyRefreshListener{
	public static int rollbackDuration = 300;	//回滚持续时间
	private int lastTotalItemCount;	//记录总条目数
	private int boundariesPosition = -1;	//自动加载的边界位置，当滑动到此位置时将会触发加载更多事件
	private boolean init;	//表示此时是初始化，用来替代ListView的OnScrollListener
	private boolean pulldown;	//标记滑动动作的方式，true：下拉；false：上拉
	private boolean full;	//标记列表是否充满
	private Scroller rollBackScroller;//回滚滚动器
	private GestureDetector gestureDetector;	//手势识别器
	private OnScrollListener onScrollListener;	//滚动监听器，用来实现滚动到底部自动加载更多功能
	private BasePulldownRefershListHeader pulldownRefershListHeader;	//下拉刷新列表头
	private BaseLoadMoreListFooter loadMoreListFooter;	//加载更多列表尾
	private OnRefreshListener onRefreshListener;	//刷新监听器
	private OnLoadMoreListener onLoadMoreListener;	//加载更多监听器
	private boolean refreshing;
	private boolean loadMoring;
	
	public SuperListView(Context context) {
		super(context);
		init();
	}

	public SuperListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	/**
	 * 初始化
	 */
	private void init(){
		rollBackScroller = new Scroller(getContext(), new AccelerateDecelerateInterpolator());
		gestureDetector = new GestureDetector(this);
		
		//设置滚动监听器
		init = true;
		setOnScrollListener(this);
		init = false;
	}
	
	@Override
	public void setOnScrollListener(OnScrollListener onScrollListener) {
		if(init){
			super.setOnScrollListener(onScrollListener);
		}else{
			this.onScrollListener = onScrollListener;
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		if(pulldownRefershListHeader != null){
			gestureDetector.onTouchEvent(ev);
			if(ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP){
				upEventHandle();
			}
		}
		return super.onTouchEvent(ev);
	}
	
	/**
	 * 弹起事件处理
	 */
	private void upEventHandle(){
		//如果当前是在第一行并且开启了下拉刷新功能
		if(getFirstVisiblePosition() == 0){
			//如果下拉刷新头正处于准备刷新状态，就进入刷新模式
			if(onRefreshListener != null && pulldownRefershListHeader.getState() == BasePulldownRefershListHeader.State.READY_REFRESH){
				refreshing = true;
				pulldownRefershListHeader.readyRefreshToRefreshingState();
				onRefreshListener.onRefresh();
			}
			//尝试回滚下拉刷新列表头
			tryRollbackPulldownRefreshListHeader();
		}
	}
	
	/**
	 * 尝试回滚下拉刷新列表头
	 */
	private void tryRollbackPulldownRefreshListHeader(int begainHeight, int endHeight){
		if(begainHeight != endHeight){
			rollBackScroller.startScroll(0, begainHeight, 0, endHeight - begainHeight, rollbackDuration);
			invalidate();
		}
	}
	
	/**
	 * 尝试回滚下拉刷新列表头
	 */
	private void tryRollbackPulldownRefreshListHeader(int begainHeight){
		tryRollbackPulldownRefreshListHeader(begainHeight, pulldownRefershListHeader.getRollbackFinalHeight());
	}
	
	/**
	 * 尝试回滚下拉刷新列表头
	 */
	private void tryRollbackPulldownRefreshListHeader(){
		tryRollbackPulldownRefreshListHeader(pulldownRefershListHeader.getContentView().getHeight());
	}
	
	/**
	 * 完成刷新
	 */
	public void finishRefresh(){
		if(pulldownRefershListHeader != null){
			pulldownRefershListHeader.toggleToRefreshingToNormalState();
			//如果当前位置在列表的顶部，就回滚，否则直接更改为最小高度
			if(getFirstVisiblePosition() == 0){
				//如果下拉刷新列表头的顶部没有跟列表的顶部正好对齐
				if((pulldownRefershListHeader.getTop() + getPaddingTop()) == getTop()){
					tryRollbackPulldownRefreshListHeader();	//回滚下拉刷新列表头
				}else{
					int begainHeight = (pulldownRefershListHeader.getContentViewHeight() + pulldownRefershListHeader.getTop()) - getPaddingTop();//因为当前下拉刷新列表头只显示了一部分，所以开始高度就是显示出来的那部分的高度
					pulldownRefershListHeader.updateHeight(begainHeight);//先将下拉刷新列表头的高度设为刚刚计算出来的开始高度
					setSelection(0);//定位到顶部
					tryRollbackPulldownRefreshListHeader(begainHeight);	//回滚下拉刷新列表头
				}
			}else{
				pulldownRefershListHeader.updateHeight(pulldownRefershListHeader.getRollbackFinalHeight());
				invalidate();
			}
			refreshing = false;
		}
	}
	
	/**
	 * 完成加载更多
	 */
	public void finishLoadMore(){
		if(loadMoreListFooter != null){
			loadMoreListFooter.toggleToNormalState();
			loadMoring = false;
		}
	}
	
	/**
	 * 刷新
	 */
	public void refresh(){
		if(pulldownRefershListHeader != null && onRefreshListener != null && pulldownRefershListHeader.getState() == BasePulldownRefershListHeader.State.NORMAL && !refreshing){
			refreshing = true;
			setSelection(0);
			pulldownRefershListHeader.setState(BasePulldownRefershListHeader.State.NORMAL_TO_REFRESHING);
			tryRollbackPulldownRefreshListHeader(pulldownRefershListHeader.getContentView().getHeight(), pulldownRefershListHeader.getContentViewHeight());
			onRefreshListener.onRefresh();
		}
	}
	
	/**
	 * 加载更多
	 */
	public void loadMore(){
		if(loadMoreListFooter != null && loadMoreListFooter.getState() == BaseLoadMoreListFooter.State.NOMRAL && onLoadMoreListener != null && full && !loadMoring){
			loadMoreListFooter.toggleToLoadingState();
			onLoadMoreListener.onLoadMore();
			setSelection(getCount() - 1);
		}
	}
	
	@Override
	public void computeScroll() {
		if (pulldownRefershListHeader != null) {	//如果有滚动的对象
			if(rollBackScroller.computeScrollOffset()){
				pulldownRefershListHeader.updateHeight(rollBackScroller.getCurrY());
			}
		}
	}

	@Override
	public void setAdapter(ListAdapter adapter) {
		//添加下拉刷新列表头
		if(pulldownRefershListHeader != null){
			pulldownRefershListHeader.setOnAllowReadyRefreshListener(this);
			addHeaderView(pulldownRefershListHeader);
		}
		
		//添加加载更多列表尾
		if(loadMoreListFooter != null){
			addFooterView(loadMoreListFooter);
		}
		super.setAdapter(adapter);
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if(onScrollListener != null){
			onScrollListener.onScrollStateChanged(view, scrollState);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		//如果开启了加载更多模式
		if(loadMoreListFooter !=null && onLoadMoreListener != null){
			loadMoreListFooter.getContentView().setVisibility((full = visibleItemCount != totalItemCount) && !refreshOrLoad?View.VISIBLE:View.GONE);	//如果列表没有充满，就隐藏加载更多列表尾，否则显示
			setFooterDividersEnabled(full);
			//如果列表充满了
			if(full && !refreshOrLoad){
				if(boundariesPosition == -1){
					if(getLastVisiblePosition() == totalItemCount - 1 - getFooterViewsCount() && loadMoreListFooter.getState() == BaseLoadMoreListFooter.State.NOMRAL){
						refreshOrLoad = true;
						boundariesPosition = getLastVisiblePosition();
						loadMoreListFooter.toggleToLoadingState();
						onLoadMoreListener.onLoadMore();
					}
				}else{
					//如果列表项个数发生改变或者又滚回去了就重置界限位置
					if(totalItemCount != lastTotalItemCount || getLastVisiblePosition() < boundariesPosition){
						boundariesPosition = -1;
					}
				}
				lastTotalItemCount = totalItemCount;	//记录总条目数
			}
		}
		
		if(onScrollListener != null){
			onScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		}
	}

	@Override
	public boolean onDown(MotionEvent e) {
		if(rollBackScroller.computeScrollOffset()){
			rollBackScroller.abortAnimation();
		}
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		pulldown = distanceY < 0;	//记录拉的方向

		//如果当前在第一行并且开启了下拉刷新模式
		if(getFirstVisiblePosition() == 0){
			if(pulldown){	//如果是下拉，直接更新下拉刷新列表头的高度
				pulldownRefershListHeader.updateHeightOffset((int) distanceY);
			}else{	//如果是上拉的话，如果当前下拉刷新列表头的高度大于其回滚的最终高度，就更新下拉刷新列表头的高度
				if(pulldownRefershListHeader.getContentView().getHeight() > pulldownRefershListHeader.getRollbackFinalHeight()){
					pulldownRefershListHeader.updateHeightOffset((int) distanceY);
					setSelection(0);
				}
			}
			if(!pulldown){
			}
		}
		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		return false;
	}

	@Override
	public boolean isAllowReadyRefresh() {
		return onRefreshListener != null && !refreshing && !loadMoring;
	}
	
	/**
	 * 获取刷新监听器
	 * @return 刷新监听器
	 */
	public OnRefreshListener getOnRefreshListener() {
		return onRefreshListener;
	}

	/**
	 * 设置刷新监听器
	 * @param onRefreshListener 刷新监听器
	 */
	public void setOnRefreshListener(OnRefreshListener onRefreshListener) {
		this.onRefreshListener = onRefreshListener;
	}

	/**
	 * 获取加载监听器
	 * @return 加载监听器
	 */
	public OnLoadMoreListener getOnLoadMoreListener() {
		return onLoadMoreListener;
	}

	/**
	 * 设置加载监听器
	 * @param onLoadMoreListener 加载监听器
	 */
	public void setOnLoadMoreListener(OnLoadMoreListener onLoadMoreListener) {
		this.onLoadMoreListener = onLoadMoreListener;
	}

	/**
	 * 获取下拉刷新列表头
	 * @return 下拉刷新列表头
	 */
	public BasePulldownRefershListHeader getPulldownRefershListHeader() {
		return pulldownRefershListHeader;
	}

	/**
	 * 设置下拉刷新列表头
	 * @param pulldownRefershListHeader 下拉刷新列表头
	 */
	public void setPulldownRefershListHeader(
			BasePulldownRefershListHeader pulldownRefershListHeader) {
		this.pulldownRefershListHeader = pulldownRefershListHeader;
	}

	/**
	 * 获取加载更多列表尾
	 * @return 加载更多列表尾
	 */
	public BaseLoadMoreListFooter getLoadMoreListFooter() {
		return loadMoreListFooter;
	}

	/**
	 * 设置加载更多列表尾
	 * @param loadMoreListFooter 加载更多列表尾
	 */
	public void setLoadMoreListFooter(BaseLoadMoreListFooter loadMoreListFooter) {
		this.loadMoreListFooter = loadMoreListFooter;
	}

	/**
	 * 刷新监听器
	 */
	public interface OnRefreshListener{
		/**
		 * 当需要刷新时回调此方法，刷新完成之后执行finishRefresh()方法即可
		 */
		public void onRefresh();
	}
	
	/**
	 * 加载更多监听器
	 */
	public interface OnLoadMoreListener{
		/**
		 * 当需要家在更多时回调此方法，加载完成之后执行finishLoadMore()方法即可
		 */
		public void onLoadMore();
	}
}