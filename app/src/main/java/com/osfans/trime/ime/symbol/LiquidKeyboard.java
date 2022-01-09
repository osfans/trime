package com.osfans.trime.ime.symbol;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.osfans.trime.R;
import com.osfans.trime.clipboard.ClipboardAdapter;
import com.osfans.trime.clipboard.ClipboardDao;
import com.osfans.trime.draft.DraftAdapter;
import com.osfans.trime.draft.DraftDao;
import com.osfans.trime.ime.core.Trime;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import com.osfans.trime.setup.Config;
import com.osfans.trime.util.YamlUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import timber.log.Timber;

public class LiquidKeyboard {
  private final Context context;

  private RecyclerView keyboardView;
  private LinearLayout parentView;
  private ClipboardAdapter mClipboardAdapter;
  private DraftAdapter mDraftAdapter;
  private SimpleAdapter simpleAdapter;
  private List<SimpleKeyBean> clipboardBeanList, draftBeanList;
  private final List<SimpleKeyBean> simpleKeyBeans;
  private List<SimpleKeyBean> historyBeans;
  private int margin_x, margin_top, single_width, parent_width, clipboard_max_size, draft_max_size;

  private int keyHeight;
  private boolean isLand;
  private SymbolKeyboardType keyboardType;
  private final String historySavePath;

  public void setView(RecyclerView view) {
    keyboardView = view;
  }

  public void setLand(boolean land) {
    isLand = land;
  }

  public LiquidKeyboard(Context context, int clipboard_max_size, int draft_max_size) {
    this.context = context;

    this.clipboard_max_size = clipboard_max_size;
    this.draft_max_size = draft_max_size;
    clipboardBeanList = ClipboardDao.get().getAllSimpleBean(clipboard_max_size);
    Timber.d("clipboardBeanList.size=%s", clipboardBeanList.size());

    draftBeanList = DraftDao.get().getAllSimpleBean(draft_max_size);
    Timber.d("draftBeanList.size=%s", draftBeanList.size());

    simpleKeyBeans = new ArrayList<>();
    historySavePath =
        context.getExternalFilesDir(null).getAbsolutePath() + File.separator + "key_history";
  }

  public void addClipboardData(String text) {
    DbBean bean = new DbBean(text);
    ClipboardDao.get().add(bean);
    clipboardBeanList.add(0, bean);
    if (mClipboardAdapter != null) mClipboardAdapter.notifyItemInserted(0);
  }

  public void addDraftData(String text) {
    DbBean bean = new DbBean(text);
    DraftDao.get().add(bean);
    draftBeanList.add(0, bean);
    if (mDraftAdapter != null) mDraftAdapter.notifyItemInserted(0);
  }

  public void select(int i) {
    TabTag tag = TabManager.getTag(i);
    calcPadding(tag.type);
    keyboardType = tag.type;
    switch (keyboardType) {
      case CLIPBOARD:
        TabManager.get().select(i);
        initClipboardData();
        break;
      case DRAFT:
        TabManager.get().select(i);
        initDraftData();
        break;
      case HISTORY:
        TabManager.get().select(i);
      default:
        initFixData(i);
    }
  }

  // 设置liquidKeyboard共用的布局参数
  public void calcPadding(int width) {

    Config config = Config.get(context);
    parent_width = width;
    final Map<String, ?> liquid_config = config.getLiquidKeyboard();

    // liquid_keyboard/margin_x定义了每个键左右两边的间隙，也就是说相邻两个键间隙是x2，而horizontal_gap定义的是spacer，使用时需要/2
    if (liquid_config != null) {
      if (liquid_config.containsKey("margin_x")) {
        Object o = YamlUtils.INSTANCE.getPixel(liquid_config, "margin_x", 0);
        margin_x = (int) o;
      }
    }

    if (margin_x == 0) {
      int horizontal_gap = config.getPixel("horizontal_gap");
      if (horizontal_gap > 1) {
        horizontal_gap = horizontal_gap / 2;
      }
      margin_x = horizontal_gap;
    }

    // 初次显示布局，需要刷新背景
    parentView = (LinearLayout) keyboardView.getParent();
    Drawable keyboardBackground =
        config.getDrawable("liquid_keyboard_background", null, null, null, null);
    if (keyboardBackground != null) parentView.setBackground(keyboardBackground);

    keyHeight = config.getLiquidPixel("key_height_land");
    if (!isLand || keyHeight <= 0) keyHeight = config.getLiquidPixel("key_height");
    margin_top = config.getLiquidPixel("vertical_gap");

    Timber.i("config keyHeight=" + keyHeight + " marginTop=" + margin_top);

    if (isLand) single_width = config.getLiquidPixel("single_width_land");
    if (single_width <= 0) single_width = config.getLiquidPixel("single_width");
    if (single_width <= 0)
      single_width = context.getResources().getDimensionPixelSize(R.dimen.simple_key_single_width);

    clipboard_max_size = config.getClipboardLimit();
  }

  // 每次点击tab都需要刷新的参数
  private void calcPadding(SymbolKeyboardType type) {

    Config config = Config.get(context);
    int[] padding = config.getKeyboardPadding();

    if (type == SymbolKeyboardType.SINGLE) {
      padding[0] =
          (parentView.getWidth() > 0 ? parentView.getWidth() : parent_width)
              % (single_width + margin_x * 2)
              / 2;
      padding[1] = padding[0];
    }
    parentView.setPadding(padding[0], 0, padding[1], 0);
    historyBeans = SimpleKeyDao.getSymbolKeyHistory(historySavePath);
  }

  public void initFixData(int i) {
    keyboardView.removeAllViews();
    mClipboardAdapter = null;
    mDraftAdapter = null;
    // 设置布局管理器
    FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(context);
    // flexDirection 属性决定主轴的方向（即项目的排列方向）。类似 LinearLayout 的 vertical 和 horizontal。
    flexboxLayoutManager.setFlexDirection(FlexDirection.ROW); // 主轴为水平方向，起点在左端。
    // flexWrap 默认情况下 Flex 跟 LinearLayout 一样，都是不带换行排列的，但是flexWrap属性可以支持换行排列。
    flexboxLayoutManager.setFlexWrap(FlexWrap.WRAP); // 按正常方向换行
    // justifyContent 属性定义了项目在主轴上的对齐方式。
    flexboxLayoutManager.setJustifyContent(JustifyContent.FLEX_START); // 交叉轴的起点对齐。

    keyboardView.removeAllViews();
    keyboardView.setLayoutManager(flexboxLayoutManager);

    // 设置适配器

    if (keyboardType == SymbolKeyboardType.HISTORY) {
      simpleKeyBeans.clear();
      simpleKeyBeans.addAll(historyBeans);
    } else {
      simpleKeyBeans.clear();
      simpleKeyBeans.addAll(TabManager.get().select(i));
    }

    Timber.d("Tab.select(%s) beans.size=%s", i, simpleKeyBeans.size());
    simpleAdapter = new SimpleAdapter(context, simpleKeyBeans);

    simpleAdapter.configStyle(single_width, keyHeight, margin_x, margin_top);
    //            simpleAdapter.configKey(single_width,height,margin_x,margin_top);
    keyboardView.setAdapter(simpleAdapter);
    // 添加分割线
    // 设置添加删除动画
    // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
    keyboardView.setSelected(true);

    // 列表适配器的点击监听事件
    simpleAdapter.setOnItemClickListener(
        (view, position) -> {
          InputConnection ic = Trime.getService().getCurrentInputConnection();
          if (ic != null) {
            SimpleKeyBean bean = simpleKeyBeans.get(position);
            ic.commitText(bean.getText(), 1);

            if (keyboardType != SymbolKeyboardType.HISTORY) {
              historyBeans.add(0, bean);
              SimpleKeyDao.saveSymbolKeyHistory(historySavePath, historyBeans);
            }
          }
        });
  }

  public void initClipboardData() {
    keyboardView.removeAllViews();
    simpleAdapter = null;

    // 设置布局管理器
    FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(context);
    // flexDirection 属性决定主轴的方向（即项目的排列方向）。类似 LinearLayout 的 vertical 和 horizontal。
    flexboxLayoutManager.setFlexDirection(FlexDirection.ROW); // 主轴为水平方向，起点在左端。
    // flexWrap 默认情况下 Flex 跟 LinearLayout 一样，都是不带换行排列的，但是flexWrap属性可以支持换行排列。
    flexboxLayoutManager.setFlexWrap(FlexWrap.WRAP); // 按正常方向换行
    // justifyContent 属性定义了项目在主轴上的对齐方式。
    flexboxLayoutManager.setJustifyContent(JustifyContent.FLEX_START); // 交叉轴的起点对齐。
    //            flexboxLayoutManager.setAlignItems(AlignItems.BASELINE);
    keyboardView.setLayoutManager(flexboxLayoutManager);

    clipboard_max_size = Config.get(context).getClipboardLimit();

    clipboardBeanList = ClipboardDao.get().getAllSimpleBean(clipboard_max_size);
    mClipboardAdapter = new ClipboardAdapter(context, clipboardBeanList);

    mClipboardAdapter.configStyle(margin_x, margin_top);

    keyboardView.setAdapter(mClipboardAdapter);
    // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
    keyboardView.setSelected(true);

    mClipboardAdapter.setOnItemClickListener(
        (view, position) -> {
          InputConnection ic = Trime.getService().getCurrentInputConnection();
          if (ic != null) {
            ic.commitText(clipboardBeanList.get(position).getText(), 1);
          }
        });
  }

  public void initDraftData() {
    keyboardView.removeAllViews();
    simpleAdapter = null;

    // 设置布局管理器
    FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(context);
    // flexDirection 属性决定主轴的方向（即项目的排列方向）。类似 LinearLayout 的 vertical 和 horizontal。
    flexboxLayoutManager.setFlexDirection(FlexDirection.ROW); // 主轴为水平方向，起点在左端。
    // flexWrap 默认情况下 Flex 跟 LinearLayout 一样，都是不带换行排列的，但是flexWrap属性可以支持换行排列。
    flexboxLayoutManager.setFlexWrap(FlexWrap.WRAP); // 按正常方向换行
    // justifyContent 属性定义了项目在主轴上的对齐方式。
    flexboxLayoutManager.setJustifyContent(JustifyContent.FLEX_START); // 交叉轴的起点对齐。
    //            flexboxLayoutManager.setAlignItems(AlignItems.BASELINE);
    keyboardView.setLayoutManager(flexboxLayoutManager);

    draft_max_size = Config.get(context).getDraftLimit();

    draftBeanList = DraftDao.get().getAllSimpleBean(draft_max_size);
    mDraftAdapter = new DraftAdapter(context, draftBeanList);

    mDraftAdapter.configStyle(margin_x, margin_top);

    keyboardView.setAdapter(mDraftAdapter);
    // 调用ListView的setSelected(!ListView.isSelected())方法，这样就能及时刷新布局
    keyboardView.setSelected(true);

    mDraftAdapter.setOnItemClickListener(
        (view, position) -> {
          InputConnection ic = Trime.getService().getCurrentInputConnection();
          if (ic != null) {
            ic.commitText(draftBeanList.get(position).getText(), 1);
          }
        });
  }
}
