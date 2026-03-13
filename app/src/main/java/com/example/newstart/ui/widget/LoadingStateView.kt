package com.example.newstart.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.example.newstart.databinding.ViewLoadingStateBinding

class LoadingStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewLoadingStateBinding

    enum class State {
        LOADING, ERROR, EMPTY, CONTENT
    }

    private var currentState: State = State.CONTENT
    private var onRetryClickListener: (() -> Unit)? = null

    init {
        binding = ViewLoadingStateBinding.inflate(LayoutInflater.from(context), this, true)
        
        binding.btnRetry.setOnClickListener {
            onRetryClickListener?.invoke()
        }
    }

    fun setState(state: State) {
        currentState = state
        
        binding.loadingContainer.visibility = if (state == State.LOADING) VISIBLE else GONE
        binding.errorContainer.visibility = if (state == State.ERROR) VISIBLE else GONE
        binding.emptyContainer.visibility = if (state == State.EMPTY) VISIBLE else GONE
    }

    fun setLoadingText(text: String) {
        binding.tvLoadingText.text = text
    }

    fun setErrorText(text: String) {
        binding.tvErrorText.text = text
    }

    fun setEmptyText(text: String) {
        binding.tvEmptyText.text = text
    }

    fun setOnRetryClickListener(listener: () -> Unit) {
        onRetryClickListener = listener
    }

    fun showLoading(text: String = "加载中...") {
        setLoadingText(text)
        setState(State.LOADING)
    }

    fun showError(text: String = "加载失败", onRetry: (() -> Unit)? = null) {
        setErrorText(text)
        onRetry?.let { setOnRetryClickListener(it) }
        setState(State.ERROR)
    }

    fun showEmpty(text: String = "暂无数据") {
        setEmptyText(text)
        setState(State.EMPTY)
    }

    fun hideAll() {
        setState(State.CONTENT)
    }
}
