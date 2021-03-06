package me.ykrank.s1next.view.fragment;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.common.base.Preconditions;

import me.ykrank.s1next.App;
import me.ykrank.s1next.AppComponent;
import me.ykrank.s1next.R;
import me.ykrank.s1next.data.api.S1Service;
import me.ykrank.s1next.data.api.model.Result;
import me.ykrank.s1next.databinding.FragmentBaseBinding;
import me.ykrank.s1next.util.ErrorUtil;
import me.ykrank.s1next.util.RxJavaUtil;
import me.ykrank.s1next.view.fragment.headless.DataRetainedFragment;
import me.ykrank.s1next.view.internal.LoadingViewModelBindingDelegate;
import me.ykrank.s1next.view.internal.LoadingViewModelBindingDelegateBaseImpl;
import me.ykrank.s1next.viewmodel.LoadingViewModel;
import rx.Observable;
import rx.Subscription;

/**
 * A base Fragment includes {@link SwipeRefreshLayout} to refresh when loading data.
 * Also wraps {@link retrofit2.Retrofit} to load data asynchronously.
 * <p>
 * We must call {@link #destroyRetainedFragment()}) if used in {@link android.support.v4.view.ViewPager}
 * otherwise leads memory leak.
 *
 * @param <D> The data we want to load.
 */
public abstract class BaseRecyclerViewFragment<D> extends BaseFragment {

    /**
     * The serialization (saved instance state) Bundle key representing
     * current loading state.
     */
    private static final String STATE_LOADING_VIEW_MODEL = "loading_view_model";

    S1Service mS1Service;

    private LoadingViewModelBindingDelegate mLoadingViewModelBindingDelegate;
    private LoadingViewModel mLoadingViewModel;

    /**
     * We use retained Fragment to retain data when configuration changes.
     */
    private DataRetainedFragment<D> mDataRetainedFragment;

    private Subscription mSubscription;


    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mLoadingViewModelBindingDelegate = getLoadingViewModelBindingDelegateImpl(inflater,
                container);
        return mLoadingViewModelBindingDelegate.getRootView();
    }

    @Override
    @CallSuper
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppComponent appComponent = App.getAppComponent(getContext());
        mS1Service = appComponent.getS1Service();

        mLoadingViewModelBindingDelegate.getSwipeRefreshLayout().setOnRefreshListener(
                this::startSwipeRefresh);
    }

    @Override
    @CallSuper
    @SuppressWarnings("unchecked")
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Indicates that this Fragment would like to
        // influence the set of actions in the Toolbar.
        setHasOptionsMenu(true);

        if (savedInstanceState == null) {
            mLoadingViewModel = new LoadingViewModel();
        } else {
            mLoadingViewModel = savedInstanceState.getParcelable(STATE_LOADING_VIEW_MODEL);
        }

        // because we can't retain Fragments that are nested in other Fragments
        // so we need to confirm this Fragment has unique tag in order to compose
        // a new unique tag for its retained Fragment.
        // Without this, we couldn't get its retained Fragment back.
        String dataRetainedFragmentTag = DataRetainedFragment.TAG + "_" +
                Preconditions.checkNotNull(getTag(), "Must add a tag to " + this + ".");
        FragmentManager fragmentManager = getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(dataRetainedFragmentTag);
        if (fragment == null) {
            mDataRetainedFragment = new DataRetainedFragment<>();
            fragmentManager.beginTransaction().add(mDataRetainedFragment, dataRetainedFragmentTag)
                    .commitAllowingStateLoss();

            // start to load data because we start this Fragment the first time
            mLoadingViewModel.setLoading(LoadingViewModel.LOADING_FIRST_TIME);
        } else {
            mDataRetainedFragment = (DataRetainedFragment<D>) fragment;

            // get data back from retained Fragment when configuration changes
            if (mDataRetainedFragment.data != null) {
                int loading = mLoadingViewModel.getLoading();
                onNext(mDataRetainedFragment.data);
                mLoadingViewModel.setLoading(loading);
            } else {
                if (!mDataRetainedFragment.stale) {
                    // start to load data because the retained Fragment was killed by system
                    // and we have no data to load
                    mLoadingViewModel.setLoading(LoadingViewModel.LOADING_FIRST_TIME);
                }
            }
        }

        mLoadingViewModelBindingDelegate.setLoadingViewModel(mLoadingViewModel);
        if (isLoading()) {
            load();
        }
    }

    @Override
    public void onDestroy() {
        //remove OnRefreshListener
        mLoadingViewModelBindingDelegate.getSwipeRefreshLayout().setOnRefreshListener(null);
        RxJavaUtil.unsubscribeIfNotNull(mSubscription);

        super.onDestroy();
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_base, menu);
    }

    @Override
    @CallSuper
    public void onPrepareOptionsMenu(Menu menu) {
        // Disables the refresh menu when loading data.
        menu.findItem(R.id.menu_refresh).setEnabled(!isLoading());
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                startSwipeRefresh();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(STATE_LOADING_VIEW_MODEL, mLoadingViewModel);
    }

    /**
     * Subclass can override this in order to provider different
     * layout for {@link LoadingViewModelBindingDelegate}.
     */
    LoadingViewModelBindingDelegate getLoadingViewModelBindingDelegateImpl(LayoutInflater inflater,
                                                                           ViewGroup container) {
        FragmentBaseBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_base,
                container, false);
        return new LoadingViewModelBindingDelegateBaseImpl(binding);
    }

    /**
     * Whether we are loading data now.
     */
    final boolean isLoading() {
        return mLoadingViewModel.getLoading() != LoadingViewModel.LOADING_FINISH;
    }

    /**
     * Whether we are pulling up to refresh.
     */
    final boolean isPullUpToRefresh() {
        return mLoadingViewModel.getLoading() == LoadingViewModel.LOADING_PULL_UP_TO_REFRESH;
    }

    /**
     * Show refresh progress and start to load new data.
     */
    public void startSwipeRefresh() {
        mLoadingViewModel.setLoading(LoadingViewModel.LOADING_SWIPE_REFRESH);
        load();
    }

    /**
     * Disables {@link SwipeRefreshLayout} and start to load new data.
     * <p>
     * Subclass should override this method and add {@link android.widget.ProgressBar}
     * to {@code getRecyclerView()} in order to let {@link #showRetrySnackbar(CharSequence)}
     * work.
     */
    @CallSuper
    void startPullToRefresh() {
        mLoadingViewModel.setLoading(LoadingViewModel.LOADING_PULL_UP_TO_REFRESH);
        load();
    }

    /**
     * Starts to load new data.
     * <p>
     * Subclass should implement {@link #getSourceObservable()}
     * in oder to provider its own data source {@link Observable}.
     */
    private void load() {
        // dismiss Snackbar in order to let user see the ProgressBar
        // when we start to load new data
        mCoordinatorLayoutAnchorDelegate.dismissSnackbarIfExist();
        mSubscription = getSourceObservable()
                .compose(RxJavaUtil.iOTransformer())
                .doOnNext(mUserValidator::validateIntercept)
                .doAfterTerminate(this::finallyDo)
                .subscribe(this::onNext, this::onError);
    }

    /**
     * Subclass should implement this in order to provider its
     * data source {@link Observable}.
     * <p>
     * The data source {@link Observable} often comes from network
     * or database.
     *
     * @return The data source {@link Observable}.
     */
    abstract Observable<D> getSourceObservable();

    /**
     * Called when a data was emitted from {@link #getSourceObservable()}.
     * <p>
     * Actually this method was only called once during loading (if no error occurs)
     * because we only emit data once from {@link #getSourceObservable()}.
     */
    @CallSuper
    void onNext(D data) {
        mDataRetainedFragment.data = data;
    }

    /**
     * A helper method consumes {@link Result}.
     * <p>
     * Sometimes we cannot get data if we have logged out or
     * have no permission to access this data.
     * This method is only used during {@link #onNext(Object)}.
     *
     * @param result The data's result we get.
     */
    final void consumeResult(Result result) {
        if (getUserVisibleHint()) {
            String message = result.getMessage();
            if (!TextUtils.isEmpty(message)) {
                showRetrySnackbar(message);
            }
        }
    }

    /**
     * Called when an error occurs during data loading.
     * <p>
     * This stops the {@link #getSourceObservable()} and it will not make
     * further calls to {@link #onNext(Object)}.
     */
    @CallSuper
    void onError(Throwable throwable) {
        if (getUserVisibleHint()) {
            showRetrySnackbar(ErrorUtil.parse(throwable));
        }
    }

    /**
     * Called if it will not make further calls to {@link #onNext(Object)}
     * or {@link #onError(Throwable)} occurred during data loading.
     */
    private void finallyDo() {
        mLoadingViewModel.setLoading(LoadingViewModel.LOADING_FINISH);
        mDataRetainedFragment.stale = true;
    }

    public void showRetrySnackbar(CharSequence text) {
        showRetrySnackbar(text, isPullUpToRefresh()
                ? v -> startPullToRefresh()
                : v -> startSwipeRefresh());
    }

    private void showRetrySnackbar(@StringRes int textResId) {
        showRetrySnackbar(getString(textResId));
    }

    final RecyclerView getRecyclerView() {
        return mLoadingViewModelBindingDelegate.getRecyclerView();
    }

    /**
     * We must call this if used in {@link android.support.v4.view.ViewPager}
     * otherwise leads memory leak.
     */
    public final void destroyRetainedFragment() {
        if (mDataRetainedFragment != null) {
            getFragmentManager().beginTransaction().remove(mDataRetainedFragment).commit();
        }
    }
}
