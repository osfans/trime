package com.osfans.trime.ime.symbol;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.RecyclerView;
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.Config;
import com.osfans.trime.databinding.LiquidKeyItemBinding;
import java.util.ArrayList;
import java.util.List;

// 显示长度不固定，字体大小正常的内容。用于类型 CANDIDATE, VAR_LENGTH
public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.ViewHolder> {
  @NonNull private final List<Rime.RimeCandidate> mCandidates = new ArrayList<>();

  private final Config theme;

  public CandidateAdapter(@NonNull Config theme) {
    this.theme = theme;
  }

  enum CommentPosition {
    UNKNOWN,
    TOP,
    BOTTOM,
    RIGHT;
  }

  public void updateCandidates(List<Rime.RimeCandidate> candidates) {
    mCandidates.clear();
    mCandidates.addAll(candidates);
  }

  @Override
  public int getItemCount() {
    return mCandidates.size();
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    final LiquidKeyItemBinding binding =
        LiquidKeyItemBinding.inflate(LayoutInflater.from(parent.getContext()));
    final boolean isCommentHidden = Rime.get_option("_hide_comment");
    if (!isCommentHidden) {
      final ConstraintSet set = new ConstraintSet();
      set.clone(binding.getRoot());
      final CommentPosition commentPosition =
          CommentPosition.values()[theme.getInt("comment_position")];
      switch (commentPosition) {
        case BOTTOM:
          set.centerHorizontally(binding.comment.getId(), ConstraintSet.PARENT_ID);
          set.centerHorizontally(binding.candidate.getId(), ConstraintSet.PARENT_ID);
          set.connect(
              binding.comment.getId(),
              ConstraintSet.TOP,
              binding.candidate.getId(),
              ConstraintSet.BOTTOM);
          set.connect(
              binding.comment.getId(),
              ConstraintSet.BOTTOM,
              ConstraintSet.PARENT_ID,
              ConstraintSet.BOTTOM);
          break;
        case TOP:
          set.centerHorizontally(binding.comment.getId(), ConstraintSet.PARENT_ID);
          set.centerHorizontally(binding.candidate.getId(), ConstraintSet.PARENT_ID);
          set.connect(
              binding.comment.getId(),
              ConstraintSet.BOTTOM,
              binding.candidate.getId(),
              ConstraintSet.TOP);
          set.connect(
              binding.comment.getId(),
              ConstraintSet.TOP,
              ConstraintSet.PARENT_ID,
              ConstraintSet.TOP);
          break;
        case RIGHT:
        case UNKNOWN:
        default:
          break;
      }
      set.applyTo(binding.getRoot());
    }
    binding.comment.setVisibility(View.GONE);
    return new ViewHolder(binding);
  }

  static class ViewHolder extends RecyclerView.ViewHolder {
    final TextView candidate, comment;

    public ViewHolder(@NonNull LiquidKeyItemBinding binding) {
      super(binding.getRoot());
      candidate = binding.candidate;
      comment = binding.comment;
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    final Rime.RimeCandidate candidate = mCandidates.get(position);

    final Typeface candidateFont = theme.getFont("candidate_font");
    final Typeface commentFont = theme.getFont("comment_font");
    holder.candidate.setTypeface(candidateFont);
    holder.comment.setTypeface(commentFont);

    holder.candidate.setText(candidate.text);
    if (holder.comment.getVisibility() == View.VISIBLE) {
      holder.comment.setText(candidate.comment);
    }

    final float candidateSize = theme.getFloat("candidate_text_size");
    final float commentSize = theme.getFloat("comment_text_size");
    if (candidateSize > 0) holder.candidate.setTextSize(candidateSize);
    if (commentSize > 0) holder.comment.setTextSize(commentSize);

    final int candidateColor = theme.getColor("candidate_text_color");
    final int commentColor = theme.getColor("comment_text_color");
    holder.candidate.setTextColor(candidateColor);
    holder.comment.setTextColor(commentColor);

    //  点击前后必须使用相同类型的背景，或者全部为背景图，或者都为背景色
    // 如果直接使用background，会造成滚动时部分内容的背景填充错误的问题
    final Drawable background =
        theme.getDrawable("key_back_color", "key_border", "key_border_color", "round_corner", null);
    if (background != null) holder.itemView.setBackground(background);

    // 如果设置了回调，则设置点击事件
    if (listener != null) {
      holder.itemView.setOnClickListener(view -> listener.onCandidateClick(position));
    }

    // 点击时产生背景变色效果
    holder.itemView.setOnTouchListener(
        (view, motionEvent) -> {
          final int hilited = theme.getColor("hilited_candidate_text_color");
          if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            holder.candidate.setTextColor(hilited);
          }
          return false;
        });
  }

  /** 添加 Listener 回调 * */
  public interface Listener {
    void onCandidateClick(int position);
  }

  private Listener listener;

  public void setListener(@NonNull Listener listener) {
    this.listener = listener;
  }
}
